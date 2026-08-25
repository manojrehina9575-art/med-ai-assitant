# Marketing site — medaiclinical.com

Static HTML in `marketing/`, deployed to Cloudflare Workers Static Assets. Config is
`wrangler.jsonc` at the repo root.

Separate from the application, which runs on AWS at `app.medaiclinical.com`.

---

## www → apex redirect

**Not** done with a `_redirects` file. Workers Static Assets only accepts *relative* paths there;
an absolute `https://www.…` source is rejected at upload with:

```
Invalid _redirects configuration: Line 2: Only relative URLs are allowed. [code: 100324]
```

Cloudflare Pages allows absolute URLs for cross-hostname redirects; Workers does not. Since
`www` and the apex are different hostnames, this has to happen at the zone level instead.

**Set it up as a Redirect Rule** (free, one per zone on the free plan):

Dashboard → your domain → **Rules → Redirect Rules → Create rule**

| | |
|---|---|
| Name | `www to apex` |
| If — custom filter | `Hostname` `equals` `www.medaiclinical.com` |
| Then | Dynamic redirect |
| Expression | `concat("https://medaiclinical.com", http.request.uri.path)` |
| Status | `301` |
| Preserve query string | on |

`www` still needs a DNS record for the rule to fire — a proxied CNAME to the apex is enough. The
rule intercepts before origin, so it never reaches the Worker.

---

## DNS, and the one setting that matters

| Record | Target | Proxy |
|---|---|---|
| `medaiclinical.com` | Worker (custom domain) | 🟠 Orange |
| `www` | CNAME to apex | 🟠 Orange |
| `app` | EC2 / ALB | ⚪ **Grey — DNS only** |

Marketing carries no patient data, so proxying it is free CDN and WAF. `app` must stay grey:
proxied means Cloudflare terminates TLS and sees PHI in plaintext, which needs a BAA they sell
only on Enterprise. Re-check `app` after any change that touches zone records.

---

## Build settings

| Field | Value |
|---|---|
| Deploy command | `npx wrangler deploy` |
| Build command | *(empty — static HTML, nothing to build)* |
| Root directory | *(empty)* |
| Output directory | *(empty)* |

Leave every directory field blank. `wrangler.jsonc` is the source of truth; a dashboard value
overrides it, and that is what published the entire React source tree on the first attempt —
Wrangler had no config, guessed `frontend`, and served 70 `.tsx` and config files publicly.

Verify locally before pushing:

```bash
npx wrangler deploy --dry-run
# must read from ./marketing, not ./frontend
```

---

## Content rules

The copy is a regulatory artifact — it is what a CDSCO or FDA reviewer reads first.

**Never:** diagnoses, detects, identifies, determines, screens for, rules out, confirms.
**Use:** drafts, prepares, flags for review, prioritises, structures, routes.

Do not add, without the evidence to back it: accuracy or sensitivity figures, "FDA cleared",
"CE marked", "HIPAA compliant" (say *designed to support*), "SOC 2 certified", customer logos or
testimonials. The "Where we actually are" section listing what is *not* built is deliberate and
should stay honest as things ship.
