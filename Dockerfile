FROM python:3.9-slim AS builder

WORKDIR /app

COPY requirements.txt .
COPY app.py .
COPY user.db ./

RUN pip wheel --no-cache-dir --no-deps --wheel-dir /app/wheels -r requirements.txt

FROM python:3.9-slim

WORKDIR /app

RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup

COPY --from=builder /app/wheels/*.whl ./
COPY app.py .
COPY user.db ./

RUN pip install --no-cache *.whl && \
    rm -rf *.whl

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8000

ENV PYTHONUNBUFFERED=1

ENTRYPOINT ["python", "app.py"]
