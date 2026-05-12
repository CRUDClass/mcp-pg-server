# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability, please do **not** open a public issue.

Instead, email [INSERT SECURITY CONTACT] with details. We will respond within
48 hours and work with you on a fix and coordinated disclosure.

## Security Considerations

- **SQL injection**: Queries are parsed with JSqlParser to validate input.
  Report any bypass techniques you discover.
- **Credentials**: Database credentials are configured via environment variables.
  Never commit `.env` files to the repository.
- **Network exposure**: The MCP endpoint (`/api/mcp`) should not be exposed to
  the public internet without authentication.
