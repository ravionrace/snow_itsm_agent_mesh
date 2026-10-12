from itsm_agents.config import Settings


def test_development_has_no_production_checks():
    assert Settings().production_problems() == []


def test_production_refuses_development_settings():
    problems = Settings(environment="production").production_problems()
    assert any("RUNTIME_API_KEY" in p for p in problems)
    assert any("MONGO_URL" in p for p in problems)
    assert any("MySQL" in p for p in problems)
    assert any("MESH_KEY" in p for p in problems)


def test_production_settings_pass(tmp_path):
    key = tmp_path / "runtime_key"
    key.write_text("r" * 40 + "\n")
    s = Settings(environment="production", runtime_api_key_file=str(key), mongo_url="mongodb://mongo:27017",
                 database_url="mysql+pymysql://mesh:pw@mysql/itsm_mesh", mcp_mesh_key="m" * 40)
    assert s.production_problems() == []
    assert s.resolved_runtime_api_key() == "r" * 40
