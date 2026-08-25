# Single-host deployment — ~$15–28/month

One EC2 instance running Docker Compose. No EKS ($73/mo saved), no ALB ($17), no RDS ($15–30),
no NAT gateway ($32). Verified end to end locally before being written down.

```
Cloudflare DNS (grey cloud) ─► EC2 ─► Caddy ─┬─► backend  /api /fhir /actuator
                                             └─► frontend  everything else
                                                   │
                                          Postgres + uploads on EBS
                                                   │
                                            nightly backup ─► S3
```

**Measured footprint: 930MB.** Backend 857MB, Postgres 48MB, Caddy 17MB, frontend 9MB.

---

## What it costs

| | ap-south-1, approx |
|---|---|
| `t4g.medium` (4GB ARM) | ~$25/mo — recommended |
| `t4g.small` (2GB ARM) | ~$12/mo — fits, but no headroom |
| 30GB gp3 EBS | ~$2.50 |
| S3 backups | cents |
| **Total** | **~$15–28/mo** |

Verify against the ap-south-1 price list; these are approximations.

> **ARM images are required.** `t4g` is Graviton, so images must be built for `linux/arm64`:
> `docker buildx build --platform linux/arm64 …`. An x86 image will not run.
>
> The ONNX embedding native was the open question and it is settled: built and run on arm64, the
> DJL tokenizer extracts `native/lib/linux-aarch64/cpu/libtokenizers.so`, the model reports its
> input tensors, and a RAG query returns 200. Memory measured at 897MB, the same as x86.

---

## One-time setup

**1. Launch the instance**

Amazon Linux 2023, **Architecture: 64-bit (Arm)**, `t4g.medium`, **30GB gp3** (the 8GB default
will not hold Postgres, uploads and images). Region `ap-south-1`.

The architecture dropdown matters: `t4g` is Graviton, and it stays greyed out while an x86_64 AMI
is selected. Verified working on ARM — the DJL tokenizer resolves its `linux-aarch64` native and
the ONNX embedding model loads, so RAG is unaffected.

Security group: **22 from your IP only**, 80 and 443 from anywhere. Nothing else — Postgres is not
published and must never be.

Allocate an **Elastic IP** and associate it after launch, or the address changes on every
stop/start and the Cloudflare record breaks.

**2. Install Docker (Amazon Linux 2023)**
```bash
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user && newgrp docker

# AL2023 does not package the compose plugin; install it manually.
DOCKER_CONFIG=/usr/local/lib/docker
sudo mkdir -p $DOCKER_CONFIG/cli-plugins
sudo curl -fsSL -o $DOCKER_CONFIG/cli-plugins/docker-compose \
  https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64
sudo chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose
docker compose version
```

Note `aarch64` in that URL — the x86 binary will not run on Graviton.

On Ubuntu 24.04 instead, it is `curl -fsSL https://get.docker.com | sudo sh` and the user is
`ubuntu`, not `ec2-user`.

**3. Give the instance an IAM role** with ECR pull and `s3:PutObject` on the backup bucket. An
instance profile means no AWS keys on the box.

**4. DNS at Cloudflare — grey cloud, not orange**
```
Type   Name   Content              Proxy
A      app    <elastic-ip>         DNS only
```
Proxied breaks Caddy's ACME challenge so no certificate is ever issued — and separately means
Cloudflare terminates TLS and sees patient data in plaintext, which needs a BAA they only sell on
Enterprise. Attach an **Elastic IP** or the address changes on every stop/start.

**5. Write `.env.prod` on the box** (never commit it)
```bash
DB_NAME=medai
DB_USERNAME=medai
DB_PASSWORD=$(openssl rand -base64 24)
DB_APP_USERNAME=medai_app
DB_APP_PASSWORD=$(openssl rand -base64 24)
JWT_SECRET=$(openssl rand -base64 64)
APP_CRYPTO_SECRET=$(openssl rand -base64 32)
GROQ_API_KEY=<your key>
APP_DOMAIN=app.medaiclinical.com
ECR_REGISTRY=<account>.dkr.ecr.ap-south-1.amazonaws.com
IMAGE_TAG=<commit-sha>
```
`JWT_SECRET` and `APP_CRYPTO_SECRET` have no safe defaults — the app refuses to start without the
first, and the second still falls back to a value committed in this repository (finding F-16).

**6. Start it**
```bash
cd med-ai-assitant/docker
docker compose -f docker-compose.prod.yml --env-file ../.env.prod up -d
```

Caddy obtains a Let's Encrypt certificate within about a minute of DNS resolving.

---

## Verify

```bash
curl -sf https://app.medaiclinical.com/actuator/health
curl -s  https://app.medaiclinical.com/fhir/metadata | jq .fhirVersion   # 4.0.1, no auth needed
curl -so /dev/null -w '%{http_code}\n' https://app.medaiclinical.com/fhir/Patient   # 401 expected

docker compose -f docker-compose.prod.yml ps          # all four healthy
docker logs medai-prod-backend-1 | grep 'storage backend'   # 'local' is correct here
```

`local` storage and no Redis are **correct** on one instance, not compromises — the warnings in
`StorageTypeValidator` and `RateLimitWindowConfig` are about replicas, and there is one.

---

## Backups — set this up on day one

`docker/backup.sh` dumps Postgres and the uploads volume to S3 nightly.

```bash
sudo cp docker/backup.sh /opt/medai/backup.sh
sudo crontab -e
# 30 2 * * *  BACKUP_BUCKET=medai-backups /opt/medai/backup.sh >> /var/log/medai-backup.log 2>&1
```

Add a lifecycle rule on the bucket to expire objects after 90 days. **Restore-test it once** —
an untested backup is a hope.

---

## Deploying a new version

```bash
export IMAGE_TAG=<commit-sha>
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker compose -f docker-compose.prod.yml --env-file ../.env.prod pull
docker compose -f docker-compose.prod.yml --env-file ../.env.prod up -d
```

There is a **gap while the backend restarts** — one instance, no rolling deploy. Seconds to a
minute. Acceptable for a demo; not for a hospital in production, which is one of several reasons
a real customer means moving to a managed setup.

**Snapshot the EBS volume before deploying anything that adds a migration.** V17 rewrites
`audit_logs` into a partitioned table and moves every row.

---

## What this is not

A PHI production environment. No managed backups beyond the cron above, no point-in-time
recovery, no failover, no rolling deploys, and the database shares a host with the application.
Fine while there are no patients on it. When a pilot signs: Postgres to RDS, then a second
instance or EKS.

Also still open regardless of hosting:
- **No SSO** — the gate on hospital IT approval
- **`APP_CRYPTO_SECRET` has a committed fallback** (F-16) — set it explicitly
- **Critical-result escalation is in-app only** — a clinician not logged in never sees it
