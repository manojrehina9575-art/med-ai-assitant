# Deploying to AWS

Target: **EKS in `ap-south-1` (Mumbai)**, domain `medaiclinical.com` registered at Cloudflare.

---

## Two decisions to get right before anything else

### 1. Do not proxy through Cloudflare

The domain is registered at Cloudflare, so the DNS record will default to **proxied** (the orange
cloud). Turn it off.

A proxied record means Cloudflare terminates TLS and sees every request in plaintext — including
patient names, medical record numbers and report content. That makes Cloudflare a processor of
personal health data, which under HIPAA needs a BAA and under the DPDP Act needs a data-processing
agreement. **Cloudflare offers a BAA only on Enterprise plans.**

Set the record to **DNS only** (grey cloud). Traffic then goes straight to the ALB and Cloudflare
sees nothing but the DNS query.

```
Type    Name    Content                                        Proxy
CNAME   app     <alb-dns-name>.ap-south-1.elb.amazonaws.com     DNS only
```

If you later want Cloudflare's WAF and caching, that is a commercial conversation with them about
an Enterprise plan and a signed BAA — not a toggle to flip.

### 2. Region is a compliance decision, not a latency one

`ap-south-1` is set throughout because the DPDP Act expects Indian patients' data to stay in India.
`us-east-1` is the AWS default and would be wrong here. Every store must be in-region: RDS, S3,
ElastiCache, ECR, ALB logs, and CloudWatch.

Note this cuts against the AI provider: if inference still goes to a US endpoint, patient data
leaves India regardless of where the cluster is. `AiProviderAdvisory` warns about this at startup;
region alone does not solve it.

---

## AWS resources to create

| Resource | Notes |
|---|---|
| **ECR** | Two repos: `medai/backend`, `medai/frontend`. Enable scan-on-push and a lifecycle policy |
| **EKS** | Private node subnets. Install the **AWS Load Balancer Controller** — the ingress needs it |
| **RDS PostgreSQL 16** | `pgvector` enabled. Encrypted with a KMS CMK, multi-AZ, automated backups, deletion protection on. **Not** in-cluster Postgres |
| **ElastiCache Redis** | Backs the cross-instance rate-limit window. Encryption in transit and at rest |
| **S3** | `medai-phi-ap-south-1`. Block all public access, SSE-KMS, versioning, and a bucket policy denying non-TLS requests |
| **S3** | `medai-alb-logs` for load-balancer access logs |
| **ACM certificate** | `app.medaiclinical.com` (+ `staging.`), validated by DNS. Add the CNAME at Cloudflare |
| **Secrets Manager** | `JWT_SECRET`, `APP_CRYPTO_SECRET`, DB passwords, provider API keys |
| **IAM: IRSA role** | For the backend service account — S3 read/write on the PHI bucket, KMS encrypt/decrypt |
| **IAM: GitHub OIDC role** | Trusts `token.actions.githubusercontent.com`. ECR push + EKS describe + Secrets read |

### pgvector on RDS

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```
Run once as the RDS master user. Flyway's `V5` assumes it exists and the app will not start without it.

---

## GitHub secrets

| Secret | Value |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | The OIDC role the pipeline assumes |
| `ECR_REGISTRY` | `<account>.dkr.ecr.ap-south-1.amazonaws.com` |
| `EKS_CLUSTER_NAME` | Cluster name |
| `ACM_CERTIFICATE_ARN` | Certificate for the ingress |

No `AWS_ACCESS_KEY_ID` or `AWS_SECRET_ACCESS_KEY`. The pipeline federates via OIDC; a long-lived
key in a repo with PHI-adjacent infrastructure is a breach waiting to be found.

Set the **production** GitHub environment to require a reviewer. Code should not reach patients
without a human approving it.

---

## Secrets: do not ship `k8s/secrets.yaml`

That file is a template with placeholders. Applying it puts credentials in the cluster from git.
Use the **External Secrets Operator** or the **Secrets Store CSI driver** to pull from Secrets
Manager at pod start.

Two the application will refuse to start without, deliberately:
- `JWT_SECRET` — no default anywhere
- `APP_CRYPTO_SECRET` — still has a committed fallback (finding F-16, unfixed). Set it explicitly
  until that is closed, or PHI gets encrypted with a key that is public in this repository

---

## First deploy

```bash
# 1. Point the cluster at the managed data stores and the PHI bucket.
helm upgrade --install medai-prod ./helm/med-ai-assistant \
  --namespace medai-prod --create-namespace \
  --set image.registry="<account>.dkr.ecr.ap-south-1.amazonaws.com" \
  --set image.backend.tag="<commit-sha>" \
  --set image.frontend.tag="<commit-sha>" \
  --set postgres.external.host="<rds-endpoint>" \
  --set redis.external.host="<elasticache-endpoint>" \
  --set storage.s3.bucket="medai-phi-ap-south-1" \
  --set serviceAccount.roleArn="<irsa-role-arn>" \
  --set ingress.certificateArn="<acm-arn>" \
  --wait

# 2. Read the ALB hostname, then create the Cloudflare CNAME — DNS only.
kubectl get ingress medai-ingress -n medai-prod \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

Flyway runs the 19 migrations on first start. **Take an RDS snapshot before deploying a release
that adds migrations** — V17 rewrites `audit_logs` into a partitioned table and moves every row.

---

## Verify after deploy

```bash
curl -sSf https://app.medaiclinical.com/actuator/health

# FHIR conformance — an EHR fetches this first and will refuse to proceed without it
curl -sS https://app.medaiclinical.com/fhir/metadata | jq .fhirVersion

# Should be 'redis (shared across instances)'. 'in-memory' means the rate ceiling is
# silently multiplied by the replica count.
kubectl logs -n medai-prod deploy/medai-prod-backend | grep 'request-rate window'

# Should say s3. 'local' with 3 replicas loses uploads.
kubectl logs -n medai-prod deploy/medai-prod-backend | grep 'File storage backend'
```

---

## Known gaps at deploy time

- **No SSO.** Hospital IT will not approve local password auth. This is the gate on any real customer.
- **`APP_CRYPTO_SECRET` has a committed default** (F-16). Set it explicitly.
- **Dev CORS patterns are compiled into production** (F-14) — `*.localhost` origin patterns. Harmless
  in practice, a finding in every pen test.
- **Critical-result escalation is in-app only.** A clinician who is not logged in never sees it.
  Discharging the notification duty properly needs SMS or paging.
