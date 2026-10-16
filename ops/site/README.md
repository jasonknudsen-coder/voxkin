# Static website deployment candidate

Target: `https://voxkin.builderofthings.studio`, on an operator-managed static web host.
Do not use unrelated project hosting, databases, credentials or deployment jobs.

Source: `site/` (landing page, privacy, support, local stylesheet). No app backend,
tracking script, third-party font or gateway credential belongs here.

Before release:
- Publisher support/feedback address: `jason@builderofthings.studio`. It is
  included in the pages and saved in TestFlight. Selected by the owner to replace the nonworking support alias; end-to-end
  delivery has not been independently tested.
- Review rendered desktop/mobile pages and privacy statements against the app.
- Use an immutable source commit and release directory under
  `/var/www/voxkin/releases/`, with current/previous symlinks.
- Inspect actual review host Caddy configuration first; add only this site's isolated
  include. Validate full Caddy configuration before reload. Preserve all other sites.
- Production authorization is required before deployment, DNS changes or activation.
- Configure DNS/TLS for the approved hostname, then verify HTTPS, all three pages,
  CSS, security headers and existing host health. Rollback restores the prior
  site pointer/config; never overwrite unrelated routes.
- Populate Apple URLs only after the public pages are verified reachable.

`voxkin.caddy` is a candidate include, not evidence of live deployment.
