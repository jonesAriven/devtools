FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1
ENV PIP_INDEX_URL=http://192.168.31.105:8081/repository/pypi-public/simple
ENV PIP_TRUSTED_HOST=192.168.31.105

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --timeout 60 --retries 10 -r requirements.txt

COPY app ./app
COPY scripts ./scripts

RUN mkdir -p /data/versions /data/backups

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]