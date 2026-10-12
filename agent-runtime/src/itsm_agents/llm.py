"""LLM gateway. Agents speak one message format (Anthropic Messages content blocks); the gateway sends
each call to the provider named in the model profile:

* ``anthropic``         - Claude through the official Anthropic SDK (third-party hosted models)
* ``openai_compatible`` - a self-hosted model behind an OpenAI-compatible API (e.g. vLLM on your GPUs)

Assistant content is returned as plain dicts and must be appended to the thread unchanged, so thinking
blocks are replayed exactly as received.
"""

from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass, field
from typing import Any, Protocol, TypeVar

import anthropic
import httpx2
from pydantic import BaseModel

from .config import ModelProfile

T = TypeVar("T", bound=BaseModel)

# Models that accept server-side refusal fallbacks in the "default" form.
_FALLBACK_MODELS = ("claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5")
_FALLBACK_BETA = "server-side-fallback-2026-07-01"


@dataclass
class Usage:
    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_tokens: int = 0
    cache_write_tokens: int = 0

    @property
    def total(self) -> int:
        return self.input_tokens + self.output_tokens + self.cache_read_tokens + self.cache_write_tokens


@dataclass
class LlmResult:
    content: list[dict[str, Any]]
    stop_reason: str
    usage: Usage
    model: str
    latency_ms: int = 0
    refusal_category: str | None = None

    @property
    def text(self) -> str:
        return "\n".join(b.get("text", "") for b in self.content if b.get("type") == "text").strip()

    @property
    def thinking(self) -> str:
        return "\n\n".join(b.get("thinking", "") for b in self.content if b.get("type") == "thinking").strip()

    @property
    def tool_uses(self) -> list[dict[str, Any]]:
        return [b for b in self.content if b.get("type") == "tool_use"]


@dataclass
class StructuredResult:
    value: Any
    usage: Usage
    model: str
    latency_ms: int = 0


class LlmGateway(Protocol):
    async def complete(self, profile: ModelProfile, system: str, messages: list[dict], tools: list[dict]) -> LlmResult: ...

    async def structured(self, profile: ModelProfile, system: str, user: str, schema: type[T]) -> StructuredResult: ...


@dataclass
class DefaultGateway:
    """Dispatches on ``profile.provider``."""

    fallbacks: bool = True
    api_key: str | None = None
    _anthropic: anthropic.AsyncAnthropic | None = field(default=None, init=False)
    _http: httpx2.AsyncClient | None = field(default=None, init=False)

    def _claude(self) -> anthropic.AsyncAnthropic:
        if self._anthropic is None:
            # An explicit key (from a mounted secret file) wins; otherwise the SDK resolves ANTHROPIC_API_KEY /
            # ANTHROPIC_AUTH_TOKEN / an `ant auth login` profile.
            self._anthropic = anthropic.AsyncAnthropic(api_key=self.api_key) if self.api_key else anthropic.AsyncAnthropic()
        return self._anthropic

    async def complete(self, profile: ModelProfile, system: str, messages: list[dict], tools: list[dict]) -> LlmResult:
        if profile.provider == "openai_compatible":
            return await self._openai_complete(profile, system, messages, tools)
        return await self._claude_complete(profile, system, messages, tools)

    async def structured(self, profile: ModelProfile, system: str, user: str, schema: type[T]) -> StructuredResult:
        if profile.provider == "openai_compatible":
            return await self._openai_structured(profile, system, user, schema)
        start = time.monotonic()
        client = self._claude().with_options(timeout=profile.timeout_s)
        response = await client.messages.parse(
            model=profile.model,
            max_tokens=profile.max_tokens,
            system=system,
            messages=[{"role": "user", "content": user}],
            output_format=schema,
        )
        if response.stop_reason == "refusal" or response.parsed_output is None:
            raise LlmRefusal(getattr(response.stop_details, "category", None) if response.stop_details else None)
        return StructuredResult(response.parsed_output, _usage(response.usage), response.model, _ms(start))

    # -- Anthropic ---------------------------------------------------------------------------------

    async def _claude_complete(self, profile: ModelProfile, system: str, messages: list[dict],
                               tools: list[dict]) -> LlmResult:
        start = time.monotonic()
        client = self._claude().with_options(timeout=profile.timeout_s)
        kwargs: dict[str, Any] = dict(
            model=profile.model,
            max_tokens=profile.max_tokens,
            # The system prompt and tool list are frozen per agent, so they form a cacheable prefix.
            system=[{"type": "text", "text": system, "cache_control": {"type": "ephemeral"}}],
            messages=messages,
            # Auto-cache the growing conversation as well.
            cache_control={"type": "ephemeral"},
        )
        if tools:
            kwargs["tools"] = tools
        if profile.effort:
            kwargs["output_config"] = {"effort": profile.effort}
        if profile.thinking:
            kwargs["thinking"] = {"type": "adaptive", "display": "summarized"}

        if self.fallbacks and profile.model.startswith(_FALLBACK_MODELS):
            response = await client.beta.messages.create(betas=[_FALLBACK_BETA], fallbacks="default", **kwargs)
        else:
            response = await client.messages.create(**kwargs)

        category = None
        if response.stop_reason == "refusal" and response.stop_details is not None:
            category = getattr(response.stop_details, "category", None)
        return LlmResult(
            content=[b.model_dump(mode="json", exclude_none=True) for b in response.content],
            stop_reason=response.stop_reason or "end_turn",
            usage=_usage(response.usage),
            model=response.model,
            latency_ms=_ms(start),
            refusal_category=category,
        )

    # -- Self-hosted (OpenAI-compatible) -----------------------------------------------------------

    def _client(self) -> httpx2.AsyncClient:
        if self._http is None:
            self._http = httpx2.AsyncClient()
        return self._http

    async def _openai_post(self, profile: ModelProfile, body: dict) -> dict:
        headers = {}
        if profile.api_key_env and os.environ.get(profile.api_key_env):
            headers["Authorization"] = f"Bearer {os.environ[profile.api_key_env]}"
        resp = await self._client().post(f"{profile.base_url.rstrip('/')}/chat/completions", json=body,
                                         headers=headers, timeout=profile.timeout_s)
        resp.raise_for_status()
        return resp.json()

    async def _openai_complete(self, profile: ModelProfile, system: str, messages: list[dict],
                               tools: list[dict]) -> LlmResult:
        start = time.monotonic()
        body: dict[str, Any] = {
            "model": profile.model,
            "max_tokens": profile.max_tokens,
            "messages": to_openai_messages(system, messages),
        }
        if tools:
            body["tools"] = [{"type": "function", "function": {"name": t["name"], "description": t.get("description", ""),
                                                               "parameters": t["input_schema"]}} for t in tools]
        data = await self._openai_post(profile, body)
        choice = data["choices"][0]
        msg = choice["message"]
        content: list[dict] = []
        if msg.get("content"):
            content.append({"type": "text", "text": msg["content"]})
        for call in msg.get("tool_calls") or []:
            try:
                args = json.loads(call["function"].get("arguments") or "{}")
            except json.JSONDecodeError:
                args = {"_invalid_json": call["function"].get("arguments")}
            content.append({"type": "tool_use", "id": call["id"], "name": call["function"]["name"], "input": args})
        stop = "tool_use" if msg.get("tool_calls") else ("max_tokens" if choice.get("finish_reason") == "length" else "end_turn")
        u = data.get("usage") or {}
        return LlmResult(content, stop, Usage(u.get("prompt_tokens", 0), u.get("completion_tokens", 0)),
                         data.get("model", profile.model), _ms(start))

    async def _openai_structured(self, profile: ModelProfile, system: str, user: str, schema: type[T]) -> StructuredResult:
        start = time.monotonic()
        data = await self._openai_post(profile, {
            "model": profile.model,
            "max_tokens": profile.max_tokens,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "response_format": {"type": "json_schema",
                                "json_schema": {"name": schema.__name__, "schema": schema.model_json_schema()}},
        })
        value = schema.model_validate_json(data["choices"][0]["message"]["content"])
        u = data.get("usage") or {}
        return StructuredResult(value, Usage(u.get("prompt_tokens", 0), u.get("completion_tokens", 0)),
                                data.get("model", profile.model), _ms(start))


class LlmRefusal(Exception):
    def __init__(self, category: str | None):
        super().__init__(f"model declined (category={category})")
        self.category = category


def to_openai_messages(system: str, messages: list[dict]) -> list[dict]:
    """Convert Anthropic-style messages to OpenAI chat format for self-hosted models."""
    out: list[dict] = [{"role": "system", "content": system}]
    for m in messages:
        content = m["content"]
        if isinstance(content, str):
            out.append({"role": m["role"], "content": content})
            continue
        if m["role"] == "assistant":
            text = "".join(b.get("text", "") for b in content if b.get("type") == "text")
            calls = [{"id": b["id"], "type": "function",
                      "function": {"name": b["name"], "arguments": json.dumps(b.get("input", {}))}}
                     for b in content if b.get("type") == "tool_use"]
            entry: dict[str, Any] = {"role": "assistant", "content": text or None}
            if calls:
                entry["tool_calls"] = calls
            out.append(entry)
        else:
            texts = []
            for b in content:
                if b.get("type") == "tool_result":
                    c = b.get("content")
                    if isinstance(c, list):
                        c = "".join(x.get("text", "") for x in c)
                    out.append({"role": "tool", "tool_call_id": b["tool_use_id"], "content": c or ""})
                elif b.get("type") == "text":
                    texts.append(b["text"])
            if texts:
                out.append({"role": "user", "content": "\n".join(texts)})
    return out


def _usage(u: Any) -> Usage:
    return Usage(
        input_tokens=getattr(u, "input_tokens", 0) or 0,
        output_tokens=getattr(u, "output_tokens", 0) or 0,
        cache_read_tokens=getattr(u, "cache_read_input_tokens", 0) or 0,
        cache_write_tokens=getattr(u, "cache_creation_input_tokens", 0) or 0,
    )


def _ms(start: float) -> int:
    return int((time.monotonic() - start) * 1000)
