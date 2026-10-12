from fastapi.testclient import TestClient

from itsm_agents import api
from itsm_agents.config import get_settings


def test_api_requires_runtime_key_and_identity(monkeypatch, tmp_path):
    monkeypatch.setenv("MESH_RUNTIME_API_KEY", "k" * 32)
    monkeypatch.setenv("MESH_DATABASE_URL", f"sqlite:///{tmp_path}/t.db")
    get_settings.cache_clear()
    try:
        with TestClient(api.app) as client:
            assert client.get("/healthz").json() == {"status": "ok"}
            body = {"message": "hi"}
            assert client.post("/v1/sessions/abcdefgh1234/turns", json=body).status_code == 401
            assert client.post("/v1/sessions/abcdefgh1234/turns", json=body,
                               headers={"X-Runtime-Key": "wrong", "X-User-Id": "u", "X-SN-Token": "t"}).status_code == 401
            assert client.post("/v1/sessions/bad id/turns", json=body,
                               headers={"X-Runtime-Key": "k" * 32, "X-User-Id": "u", "X-SN-Token": "t"}).status_code in (400, 404)
            assert client.post("/v1/sessions/abcdefgh1234/confirmations/x", json={"decision": "maybe"},
                               headers={"X-Runtime-Key": "k" * 32, "X-User-Id": "u", "X-SN-Token": "t"}).status_code == 422
            # progress needs the runtime key and user, not a ServiceNow token
            assert client.get("/v1/sessions/abcdefgh1234/progress", headers={"X-User-Id": "u"}).status_code == 401
            assert client.get("/v1/sessions/abcdefgh1234/progress",
                              headers={"X-Runtime-Key": "k" * 32, "X-User-Id": "u"}).json() == {"steps": []}
    finally:
        get_settings.cache_clear()
