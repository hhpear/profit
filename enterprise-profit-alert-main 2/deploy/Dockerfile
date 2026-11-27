FROM python:3.11.14-slim-bookworm
WORKDIR /app
COPY . /app
RUN apt-get update && apt-get install -y --no-install-recommends tzdata && rm -rf /var/lib/apt/lists/*
RUN python -m pip install --no-deps -r deploy/requirements-runtime.lock && useradd --uid 10001 --create-home profit
USER 10001:10001
ENV PYTHONUNBUFFERED=1 TZ=Asia/Shanghai
ENTRYPOINT ["python", "-m", "scripts.profit_alert_ops"]
CMD ["run", "--state-dir", "/state", "--mode", "dry-run"]
