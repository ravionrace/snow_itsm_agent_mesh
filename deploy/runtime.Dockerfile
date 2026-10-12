FROM python:3.13-slim
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY agent-runtime/pyproject.toml ./
COPY agent-runtime/src ./src
RUN pip install --no-cache-dir . && mkdir -p /data && chown 10001 /data
USER 10001
EXPOSE 8090
CMD ["uvicorn", "itsm_agents.api:app", "--host", "0.0.0.0", "--port", "8090"]
