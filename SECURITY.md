# Security Policy

## Reporting Security Vulnerabilities

If you discover a security vulnerability in this connector, please report it by:

1. **Do NOT create a public GitHub issue** for security vulnerabilities
2. Email the maintainers directly or use GitHub's private vulnerability reporting feature
3. Include detailed information about the vulnerability and steps to reproduce

## Known Security Incidents

### Credential Exposure (November 2025)

**Incident:** Camunda SaaS API credentials were accidentally committed to version control in the following files:
- `docker-compose.yml`
- `src/main/resources/application.properties`

**Impact:** Test cluster credentials were exposed publicly in the Git history.

**Resolution:**
- Test cluster credentials were rotated immediately
- Files added to `.gitignore` to prevent future commits
- Template files created (`*.template`) for safe credential management
- Repository cleaned and documentation updated

**Lessons Learned:**
- Never commit credentials to version control
- Use environment variables or secrets management systems
- Implement pre-commit hooks to detect credentials
- Regular security audits of configuration files

## Best Practices for Credentials Management

### Using This Connector

1. **Never commit credentials:**
   - Use the provided `.template` files
   - Copy templates to actual config files
   - Ensure config files are in `.gitignore`

2. **For local development:**
   ```bash
   cp docker-compose.yml.template docker-compose.yml
   cp src/main/resources/application.properties.template src/main/resources/application.properties
   # Edit files with your credentials
   # Files are gitignored and won't be committed
   ```

3. **For production deployment:**
   - Use environment variables
   - Use secrets management systems (Kubernetes Secrets, AWS Secrets Manager, etc.)
   - Rotate credentials regularly
   - Use least-privilege access principles

4. **For CI/CD:**
   - Store credentials in GitHub Secrets or similar
   - Never echo or log credentials
   - Use short-lived tokens when possible

### Git Pre-commit Hook

Consider adding a pre-commit hook to detect credentials:

```bash
#!/bin/sh
# .git/hooks/pre-commit

# Check for common credential patterns
if git diff --cached | grep -iE "(client.secret|password|api.key|bearer.token)" > /dev/null; then
    echo "Error: Potential credentials detected in commit!"
    echo "Please review and remove credentials before committing."
    exit 1
fi
```

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |

## Security Considerations

### PDF Processing

- **File Size Limits:** Processing very large PDFs can cause memory issues. Consider implementing file size limits based on available resources.
- **Input Validation:** The connector validates page ranges and document structure, but malformed PDFs may still cause processing errors.
- **Temporary Files:** Ensure temporary files are properly cleaned up after processing.

### Connector Deployment

- **Network Security:** When deployed with Docker, ensure proper network isolation and firewall rules.
- **Resource Limits:** Set appropriate Docker resource limits to prevent DoS through large file processing.
- **Logging:** Avoid logging sensitive document content or metadata.

## Updates and Patches

Security updates will be released as needed. Monitor this repository for:
- Security advisories
- Dependency updates
- Critical bug fixes

To update:
```bash
git pull origin main
mvn clean package
docker-compose up --build -d
```

## Contact

For security concerns, please contact the repository maintainers through GitHub's security advisory feature.
