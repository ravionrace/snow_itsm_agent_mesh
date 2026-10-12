"""Runtime settings. Every value can be overridden with a MESH_ environment variable
(nested fields use a double underscore, e.g. MESH_DOMAIN__EFFORT=high)."""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class ModelProfile(BaseModel):
    """One model behind the LLM gateway. ``anthropic`` uses the official Anthropic SDK;
    ``openai_compatible`` targets a self-hosted endpoint such as vLLM."""

    provider: Literal["anthropic", "openai_compatible"] = "anthropic"
    model: str
    effort: Literal["low", "medium", "high", "xhigh", "max"] | None = None
    # Adaptive extended thinking with a summarized display, so the chat can show how the agent reasoned.
    thinking: bool = False
    max_tokens: int = 16000
    base_url: str | None = None
    api_key_env: str | None = None
    timeout_s: float = 120.0


class Budget(BaseModel):
    """Hard caps per user turn, enforced in code by the harness."""

    max_model_calls: int = 10
    max_tool_calls: int = 12
    max_total_tokens: int = 300_000
    max_seconds: float = 150.0
    max_handoffs: int = 2
    max_grounding_retries: int = 1


class Price(BaseModel):
    """USD per million tokens."""

    input: float
    output: float
    cache_read: float
    cache_write: float


DEFAULT_PRICES: dict[str, Price] = {
    "claude-opus-5-5": Price(input=4.0, output=20.0, cache_read=0.20, cache_write=5.0),
    "claude-sonnet-5-5": Price(input=2.0, output=10.0, cache_read=0.20, cache_write=2.5),
    "claude-haiku-4-5": Price(input=1.0, output=5.0, cache_read=0.10, cache_write=1.25),
}


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MESH_", env_nested_delimiter="__", extra="ignore")

    # LLM gateway. A cheap model routes intents; stronger models do domain reasoning and change risk.
    router: ModelProfile = ModelProfile(model="claude-haiku-4-5", max_tokens=1024, timeout_s=30)
    domain: ModelProfile = ModelProfile(model="claude-opus-5-5", effort="medium", thinking=True)
    risk: ModelProfile = ModelProfile(model="claude-opus-5-5", effort="high", thinking=True)
    summarizer: ModelProfile = ModelProfile(model="claude-haiku-4-5", max_tokens=2048, timeout_s=60)
    # Server-side refusal fallback ("default" routing by refusal category) on models that support it.
    anthropic_fallbacks: bool = True

    # One MCP server per domain, each its own Spring Boot service.
    mcp_servers: dict[str, str] = Field(default_factory=lambda: {
        "incident": "http://localhost:8081/mcp",
        "problem": "http://localhost:8082/mcp",
        "change": "http://localhost:8083/mcp",
        "catalog": "http://localhost:8084/mcp",
        "cmdb_kb": "http://localhost:8085/mcp",
        "access": "http://localhost:8086/mcp",
    })
    mcp_timeouts_s: dict[str, float] = Field(default_factory=lambda: {
        "incident": 20, "problem": 20, "change": 30, "catalog": 30, "cmdb_kb": 15, "access": 10,
    })
    mcp_mesh_key: str = ""
    mcp_mesh_key_file: str = ""

    # Session store (LangGraph checkpoints + traces). Empty = in-memory (dev and tests only).
    mongo_url: str = ""
    mongo_db: str = "itsm_mesh"
    session_ttl_s: int = 7 * 24 * 3600

    # Cost tracker, eval results and tool-call audit log.
    database_url: str = "sqlite:///./itsm_mesh.db"
    database_url_file: str = ""
    # Anthropic API key from a mounted file; when empty the SDK's own resolution (ANTHROPIC_API_KEY, ...) applies.
    anthropic_api_key_file: str = ""

    # Shared secret the Spring Boot orchestrator API sends as X-Runtime-Key.
    runtime_api_key: str = ""
    runtime_api_key_file: str = ""

    # "production" refuses development-only settings at start-up (production_problems()).
    environment: str = "development"

    budget: Budget = Budget()
    prices: dict[str, Price] = Field(default_factory=lambda: dict(DEFAULT_PRICES))

    # Long agent threads are summarised into a fresh thread above this size (characters).
    thread_compact_chars: int = 120_000
    evidence_max_items: int = 300

    def profile(self, name: str) -> ModelProfile:
        return getattr(self, name)

    def resolved_runtime_api_key(self) -> str:
        return _secret(self.runtime_api_key_file, self.runtime_api_key)

    def resolved_mesh_key(self) -> str:
        return _secret(self.mcp_mesh_key_file, self.mcp_mesh_key)

    def resolved_database_url(self) -> str:
        return _secret(self.database_url_file, self.database_url)

    def resolved_anthropic_api_key(self) -> str | None:
        return _secret(self.anthropic_api_key_file, "") or None

    def production_problems(self) -> list[str]:
        """Settings that are only safe on a developer machine. Empty unless environment == production."""
        if self.environment.lower() != "production":
            return []
        problems = []
        if len(self.resolved_runtime_api_key()) < 32:
            problems.append("MESH_RUNTIME_API_KEY(_FILE) must be set (32+ chars) so only the orchestrator API can call the runtime")
        if not self.mongo_url:
            problems.append("MESH_MONGO_URL is required (in-memory sessions are lost on restart and not shared)")
        if self.resolved_database_url().startswith("sqlite"):
            problems.append("MESH_DATABASE_URL must point to MySQL, not SQLite")
        if len(self.resolved_mesh_key()) < 32:
            problems.append("MESH_MCP_MESH_KEY must be set (32+ chars) to authenticate to the MCP servers")
        insecure = [s for s, u in self.mcp_servers.items() if not u.startswith(("https://", "http://mcp-", "http://localhost"))]
        if insecure:
            problems.append(f"MCP server URLs must be https or cluster-internal: {', '.join(insecure)}")
        return problems


def _secret(file: str, fallback: str) -> str:
    """Secrets come from mounted files in production (Kubernetes / Docker secrets); the value is a dev fallback."""
    if file:
        with open(file, encoding="utf-8") as f:
            return f.read().strip()
    return fallback


@lru_cache
def get_settings() -> Settings:
    return Settings()
