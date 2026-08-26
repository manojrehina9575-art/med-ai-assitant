/**
 * Maps between hospital workspaces and the hostnames they are served on.
 *
 * <p>Every hospital reaches the platform at its own host — `lifeline.medaiclinical.com` — which is
 * what makes tenancy unambiguous end to end. The refresh cookie is host-only, so one hospital's
 * session is never even transmitted to another's hostname, and because the SPA calls a relative
 * `/api`, each tenant's requests are same-origin with the page that issued them.
 *
 * <p>`app.<base>` is not a tenant. It is the workspace chooser: it takes a workspace name and
 * sends the browser to that tenant's own host, where the actual sign-in happens.
 */

/**
 * Hosts that belong to the platform rather than to a hospital.
 *
 * <p>Without this list the login page read `app` out of `app.medaiclinical.com` as a workspace
 * slug, asked the API for a hospital named "app", and turned every sign-in into "Hospital
 * workspace not found" — while hiding the field needed to correct it.
 */
const RESERVED_HOSTS = new Set(['app', 'www', 'api', 'staging', 'admin']);

/** Local hostnames, where there is no base domain to append a workspace to. */
function isLocalHost(hostname: string): boolean {
  return (
    hostname === 'localhost' ||
    hostname.endsWith('.localhost') ||
    /^\d{1,3}(\.\d{1,3}){3}$/.test(hostname)
  );
}

/**
 * The workspace this hostname identifies, or '' when it identifies none.
 *
 * <p>Requires at least three labels so an apex domain (`medaiclinical.com`) is not read as a
 * workspace named "medaiclinical". Returning '' is what makes the login form render its workspace
 * input instead of assuming one.
 */
export function getTenantFromHostname(hostname: string = window.location.hostname): string {
  if (isLocalHost(hostname)) {
    // `lifeline.localhost` is how tenants are exercised against the dev server.
    const local = hostname.endsWith('.localhost') ? hostname.split('.')[0] : '';
    return RESERVED_HOSTS.has(local) ? '' : local;
  }
  const parts = hostname.split('.');
  if (parts.length < 3) return '';
  const label = parts[0].toLowerCase();
  return RESERVED_HOSTS.has(label) ? '' : label;
}

/** The registrable domain shared by every tenant host, e.g. `medaiclinical.com`. */
export function getBaseDomain(hostname: string = window.location.hostname): string {
  if (isLocalHost(hostname)) {
    return hostname.endsWith('.localhost') ? hostname.split('.').slice(1).join('.') : hostname;
  }
  const parts = hostname.split('.');
  return parts.length < 3 ? hostname : parts.slice(1).join('.');
}

/**
 * Absolute URL of a workspace's own host.
 *
 * <p>The port is preserved so this stays correct against the dev server, where tenants live at
 * `lifeline.localhost:5173`.
 */
export function tenantUrl(workspace: string, path: string = '/login'): string {
  const { protocol, port } = window.location;
  const suffix = port ? `:${port}` : '';
  return `${protocol}//${workspace}.${getBaseDomain()}${suffix}${path}`;
}
