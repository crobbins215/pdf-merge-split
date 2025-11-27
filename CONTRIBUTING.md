# Contributing to PDF Merge & Split Connector

Thank you for your interest in contributing to the PDF Merge & Split Connector! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and collaborative environment.

## How to Contribute

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce** the issue
- **Expected vs actual behavior**
- **Environment details** (Java version, Camunda version, OS)
- **PDF samples** (if applicable and non-sensitive)
- **Error logs** or stack traces

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion:

- **Use a clear title** describing the enhancement
- **Provide detailed description** of the proposed functionality
- **Explain why this enhancement would be useful** to most users
- **List any alternatives** you've considered

### Pull Requests

1. **Fork the repository** and create your branch from `main`
2. **Write tests** for any new functionality
3. **Ensure all tests pass** (`mvn clean verify`)
4. **Update documentation** if needed
5. **Follow the coding style** of the project
6. **Write clear commit messages**

#### Development Setup

```bash
# Clone your fork
git clone https://github.com/YOUR-USERNAME/pdf-merge-split.git
cd pdf-merge-split

# Build the project
mvn clean package

# Run tests
mvn clean verify
```

#### Coding Standards

- **Java 21** is required
- Follow **standard Java conventions**
- Add **JavaDoc** for public methods and classes
- Keep methods **focused and concise**
- Use **meaningful variable names**
- **Add unit tests** for all new features

#### Testing Requirements

- All new features must include unit tests
- Maintain or improve code coverage
- Tests must pass on all supported platforms
- Use descriptive test method names (e.g., `shouldMergeTwoPdfsSuccessfully`)

#### Commit Messages

- Use the present tense ("Add feature" not "Added feature")
- Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit the first line to 72 characters
- Reference issues and pull requests liberally

Examples:
```
Add split by bookmark feature

- Implement bookmark detection
- Add unit tests for bookmark splitting
- Update README with new operation

Fixes #123
```

## Development Workflow

### Branch Naming

- `feature/your-feature-name` - New features
- `bugfix/issue-description` - Bug fixes
- `docs/what-you-changed` - Documentation updates

### Before Submitting

1. ✅ Run `mvn clean verify` - All tests pass
2. ✅ Update README if adding new features
3. ✅ Add/update element template if changing inputs
4. ✅ Test with Docker deployment
5. ✅ Check for credential leaks in commits

### Review Process

- Maintainers will review your PR
- Address any requested changes
- Once approved, your PR will be merged

## Testing

### Running Unit Tests

```bash
mvn clean test
```

### Running Integration Tests

```bash
mvn clean verify
```

### Testing with Docker

```bash
# Build connector
mvn clean package

# Start Docker container
docker-compose up --build -d

# View logs
docker-compose logs -f pdf-connector
```

### Testing in Camunda

1. Deploy connector to your Camunda cluster
2. Import example BPMN from `examples/` directory
3. Start a process instance
4. Verify connector executes successfully

## Project Structure

```
pdf-merge-split/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/camunda/
│   │   │       ├── dto/           # Request/Response DTOs
│   │   │       └── example/
│   │   │           └── operations/ # Connector implementation
│   │   └── resources/
│   │       └── META-INF/services/  # SPI configuration
│   └── test/
│       └── java/                   # Unit tests
├── element-templates/              # Element template (auto-generated)
├── examples/                       # Example BPMN processes
└── README.md
```

## Key Files

- **PdfConnectorProvider.java** - Main connector implementation
- **PdfOperations.java** - PDF manipulation logic
- **PdfConnectorRequest.java** - Input DTOs with validation
- **PdfConnectorResult.java** - Output DTOs
- **PdfConnectorTest.java** - Unit tests
- **element-templates/pdf-connector.json** - Auto-generated element template

## Building for Release

```bash
# Update version in pom.xml
mvn versions:set -DnewVersion=X.Y.Z

# Build and test
mvn clean verify

# Create release tag
git tag -a vX.Y.Z -m "Release vX.Y.Z"
git push origin vX.Y.Z
```

## Additional Resources

- [Camunda Connector SDK Documentation](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/)
- [Apache PDFBox Documentation](https://pdfbox.apache.org/documentation/)
- [Camunda Community Hub](https://github.com/camunda-community-hub)

## Questions?

Feel free to open an issue with the `question` label if you have any questions about contributing.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
