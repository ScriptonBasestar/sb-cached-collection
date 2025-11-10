# Contributing to SB Cached Collection

Thank you for your interest in contributing to SB Cached Collection! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Commit Message Convention](#commit-message-convention)
- [Pull Request Process](#pull-request-process)
- [Issue Reporting](#issue-reporting)
- [Community](#community)

---

## Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inclusive experience for everyone. We expect all contributors to:

- Use welcoming and inclusive language
- Be respectful of differing viewpoints and experiences
- Gracefully accept constructive criticism
- Focus on what is best for the community
- Show empathy towards other community members

### Unacceptable Behavior

- Harassment, discrimination, or offensive comments
- Trolling or insulting/derogatory remarks
- Publishing others' private information without permission
- Any conduct which could reasonably be considered inappropriate

---

## Getting Started

### Prerequisites

- **Java**: JDK 8 or higher
- **Maven**: 3.6.0 or higher
- **Git**: For version control
- **IDE**: IntelliJ IDEA (recommended) or Eclipse

### Fork and Clone

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/sb-cached-collection.git
   cd sb-cached-collection
   ```

3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/scriptonbasestar/sb-cached-collection.git
   ```

4. **Keep your fork in sync**:
   ```bash
   git fetch upstream
   git merge upstream/master
   ```

---

## Development Setup

### Building the Project

```bash
# Build all modules
mvn clean install

# Build without running tests (faster)
mvn clean install -DskipTests

# Build specific module
cd cache-collection
mvn clean install
```

### Running Tests

```bash
# Run all tests
mvn test

# Run tests for specific module
cd cache-collection
mvn test

# Run specific test class
mvn test -Dtest=SBCacheMapTest

# Run specific test method
mvn test -Dtest=SBCacheMapTest#testBasicGetPut
```

### IDE Setup

#### IntelliJ IDEA

1. **Import Project**:
   - File → Open → Select `pom.xml`
   - Choose "Open as Project"

2. **Configure Code Style**:
   - File → Settings → Editor → Code Style → Java
   - Scheme: Default
   - Tabs and Indents: 4 spaces (no tabs)

3. **Enable Annotation Processing**:
   - File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - Check "Enable annotation processing"

#### Eclipse

1. **Import Project**:
   - File → Import → Maven → Existing Maven Projects
   - Select project root directory

2. **Configure Formatter**:
   - Window → Preferences → Java → Code Style → Formatter
   - Use 4 spaces for indentation

---

## How to Contribute

### Types of Contributions

1. **Bug Fixes**: Fix existing issues
2. **New Features**: Implement new functionality
3. **Documentation**: Improve or add documentation
4. **Tests**: Add or improve test coverage
5. **Performance**: Optimize existing code
6. **Refactoring**: Improve code quality without changing behavior

### Contribution Workflow

1. **Check existing issues**: Look for related issues or create a new one
2. **Discuss before implementing**: For major changes, discuss in the issue first
3. **Create a branch**: Use descriptive branch names
4. **Make changes**: Follow coding standards
5. **Write tests**: Ensure new code is tested
6. **Update documentation**: If needed
7. **Submit PR**: Follow PR guidelines

---

## Coding Standards

### Java Code Style

#### Naming Conventions

```java
// Classes: PascalCase
public class SBCacheMap<K, V> { }

// Interfaces: PascalCase with descriptive names
public interface EvictionPolicy<K, V> { }

// Methods: camelCase with verb prefixes
public V get(K key) throws SBCacheLoadFailException { }

// Variables: camelCase
private final ConcurrentHashMap<K, V> data;

// Constants: UPPER_SNAKE_CASE
private static final int DEFAULT_TIMEOUT_SEC = 60;

// Generics: Single capital letters
public class SBCacheMap<K, V> { }  // K=Key, V=Value
```

#### Code Formatting

```java
// Use 4 spaces for indentation (no tabs)
public void example() {
    if (condition) {
        doSomething();
    }
}

// Opening braces on same line
public class MyClass {
    public void myMethod() {
        // code
    }
}

// One statement per line
int x = 1;
int y = 2;

// Maximum line length: 120 characters
```

#### Documentation

```java
/**
 * Creates a new cache with the specified configuration.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 * @param timeoutSec the timeout in seconds for cache entries
 * @param loader the loader function to load values when not present
 * @return a new SBCacheMap instance
 * @throws IllegalArgumentException if timeoutSec is negative
 */
public static <K, V> SBCacheMap<K, V> create(
    int timeoutSec,
    Function<K, V> loader
) {
    // implementation
}
```

### Best Practices

#### 1. Null Safety

```java
// Always check for null
public void setLoader(Function<K, V> loader) {
    if (loader == null) {
        throw new IllegalArgumentException("Loader cannot be null");
    }
    this.loader = loader;
}

// Use Optional for nullable return values
public Optional<V> findValue(K key) {
    V value = data.get(key);
    return Optional.ofNullable(value);
}
```

#### 2. Exception Handling

```java
// Use specific exceptions
public V get(K key) throws SBCacheLoadFailException {
    try {
        return loadValue(key);
    } catch (Exception e) {
        throw new SBCacheLoadFailException("Failed to load key: " + key, e);
    }
}

// Don't swallow exceptions
try {
    riskyOperation();
} catch (IOException e) {
    // Log or rethrow - don't ignore
    logger.error("Operation failed", e);
    throw new RuntimeException("Operation failed", e);
}
```

#### 3. Thread Safety

```java
// Use synchronized blocks for critical sections
private final Object lock = new Object();

public void updateCache(K key, V value) {
    synchronized (lock) {
        data.put(key, value);
        updateMetrics();
    }
}

// Document thread-safety guarantees
/**
 * Thread-safe cache implementation.
 * All public methods are synchronized and safe for concurrent access.
 */
public class SBCacheMap<K, V> { }
```

#### 4. Resource Management

```java
// Always implement AutoCloseable for resources
public class SBCacheMap<K, V> implements AutoCloseable {

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

// Use try-with-resources
try (SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
    .timeoutSec(60)
    .build()) {
    // use cache
} // automatically closed
```

---

## Testing Guidelines

### Test Structure

```java
public class SBCacheMapTest {

    private SBCacheMap<Integer, String> cache;
    private Function<Integer, String> loader;

    @Before
    public void setUp() {
        loader = key -> "Value-" + key;
        cache = SBCacheMap.<Integer, String>builder()
            .loader(loader)
            .timeoutSec(60)
            .build();
    }

    @After
    public void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }

    @Test
    public void testBasicGetPut() throws SBCacheLoadFailException {
        // Given
        Integer key = 1;
        String expectedValue = "Value-1";

        // When
        String actualValue = cache.get(key);

        // Then
        Assert.assertEquals(expectedValue, actualValue);
    }
}
```

### Test Naming Convention

```java
// Pattern: test<MethodName>_<Scenario>_<ExpectedResult>

@Test
public void testGet_WhenKeyExists_ReturnsValue() { }

@Test
public void testGet_WhenKeyNotExists_LoadsFromLoader() { }

@Test(expected = SBCacheLoadFailException.class)
public void testGet_WhenLoaderFails_ThrowsException() { }

@Test
public void testPut_WithNullValue_ThrowsIllegalArgumentException() { }
```

### Test Coverage Requirements

- **Minimum coverage**: 80% line coverage
- **Critical paths**: 100% coverage for core logic
- **Edge cases**: Test boundary conditions
- **Error paths**: Test exception scenarios

```bash
# Generate coverage report
mvn clean test jacoco:report

# View report at: target/site/jacoco/index.html
```

### Test Categories

#### Unit Tests
```java
// Test single method in isolation
@Test
public void testEvict_RemovesOldestEntry() {
    cache.put(1, "A");
    cache.put(2, "B");
    cache.evict();
    Assert.assertNull(cache.getIfPresent(1));
}
```

#### Integration Tests
```java
// Test multiple components together
@Test
public void testCacheWithAsyncLoader() throws Exception {
    SBCacheMap<Integer, String> asyncCache = SBCacheMap.<Integer, String>builder()
        .loader(loader)
        .loadStrategy(LoadStrategy.ASYNC)
        .build();

    String value = asyncCache.get(1);
    Assert.assertEquals("Value-1", value);
}
```

#### Performance Tests
```java
@Test
public void testHighConcurrency() throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(100);
    CountDownLatch latch = new CountDownLatch(1000);

    for (int i = 0; i < 1000; i++) {
        final int key = i;
        executor.submit(() -> {
            try {
                cache.get(key);
            } catch (Exception e) {
                // handle
            } finally {
                latch.countDown();
            }
        });
    }

    boolean completed = latch.await(10, TimeUnit.SECONDS);
    Assert.assertTrue("Should complete within 10 seconds", completed);
}
```

---

## Commit Message Convention

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- **feat**: A new feature
- **fix**: A bug fix
- **docs**: Documentation only changes
- **style**: Code style changes (formatting, missing semicolons, etc.)
- **refactor**: Code change that neither fixes a bug nor adds a feature
- **perf**: Performance improvements
- **test**: Adding or updating tests
- **build**: Changes to build system or dependencies
- **ci**: Changes to CI configuration
- **chore**: Other changes that don't modify src or test files

### Scopes

Use module names or feature areas:
- `cache-core`
- `cache-collection`
- `cache-loader-db`
- `cache-spring`
- `docs`

### Examples

```bash
# Feature addition
feat(cache-collection): add ReferenceType support for GC cooperation

Added STRONG, SOFT, and WEAK reference types to allow garbage collector
to reclaim cache entries when memory is low.

Closes #123

# Bug fix
fix(cache-spring): resolve CacheManager bean initialization order issue

Fixed NullPointerException when CacheManager is initialized before
DataSource by adding @DependsOn annotation.

Fixes #456

# Documentation
docs(readme): update Spring Boot integration example

Added complete Spring Boot Auto-Configuration example with YAML
configuration and @Cacheable usage.

# Performance improvement
perf(cache-collection): optimize LRU eviction with LinkedHashMap

Replaced custom doubly-linked list with LinkedHashMap for 30% faster
eviction performance in high-throughput scenarios.

# Test addition
test(cache-collection): add concurrent access test for ReferenceType

Added test with 100 threads accessing WEAK reference cache to verify
thread safety under high concurrency.
```

### Commit Message Rules

1. **Subject line**:
   - Max 72 characters
   - Imperative mood ("add" not "added")
   - No period at the end
   - Lowercase after colon

2. **Body** (optional):
   - Wrap at 72 characters
   - Explain what and why, not how
   - Separate from subject with blank line

3. **Footer** (optional):
   - Reference issues: `Closes #123`, `Fixes #456`
   - Breaking changes: `BREAKING CHANGE: ...`

---

## Pull Request Process

### Before Submitting

1. **Sync with upstream**:
   ```bash
   git fetch upstream
   git rebase upstream/master
   ```

2. **Run all tests**:
   ```bash
   mvn clean test
   ```

3. **Check code style**:
   ```bash
   mvn checkstyle:check
   ```

4. **Update documentation** if needed

5. **Add tests** for new features

### Creating a Pull Request

1. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Open PR** on GitHub with:
   - Clear title following commit convention
   - Description of changes
   - Related issue numbers
   - Screenshots/examples if applicable

### PR Template

```markdown
## Description
Brief description of the changes

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to change)
- [ ] Documentation update

## Related Issues
Closes #123
Related to #456

## How Has This Been Tested?
Describe the tests you ran and how to reproduce

## Checklist
- [ ] My code follows the project's coding standards
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code where necessary
- [ ] I have updated the documentation accordingly
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
- [ ] Any dependent changes have been merged and published
```

### Review Process

1. **Automated checks**: CI must pass
2. **Code review**: At least one maintainer approval required
3. **Testing**: Reviewers may request additional tests
4. **Discussion**: Address review comments
5. **Merge**: Maintainer will merge when approved

### After Merge

1. **Delete your branch**:
   ```bash
   git branch -d feature/your-feature-name
   git push origin --delete feature/your-feature-name
   ```

2. **Sync your fork**:
   ```bash
   git checkout master
   git pull upstream master
   git push origin master
   ```

---

## Issue Reporting

### Before Creating an Issue

1. **Search existing issues**: Check if already reported
2. **Update to latest version**: Bug may be already fixed
3. **Check documentation**: May be usage issue, not a bug

### Bug Report Template

```markdown
**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Create cache with '...'
2. Call method '...'
3. See error

**Expected behavior**
What you expected to happen.

**Actual behavior**
What actually happened.

**Code example**
```java
SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
    .timeoutSec(60)
    .build();
// code that triggers the bug
```

**Environment**
- Java version: [e.g., JDK 8, JDK 11]
- SB Cached Collection version: [e.g., 0.1.0]
- Spring Boot version (if applicable): [e.g., 2.7.0]
- OS: [e.g., Ubuntu 20.04, Windows 10]

**Stack trace**
```
Paste stack trace here if applicable
```

**Additional context**
Any other information about the problem.
```

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
A clear description of what the problem is.

**Describe the solution you'd like**
What you want to happen.

**Describe alternatives you've considered**
Other solutions or features you've considered.

**Use case**
Describe your use case and why this feature would be valuable.

**Example usage**
```java
// How you envision using this feature
SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
    .newFeature(true)
    .build();
```

**Additional context**
Any other context or screenshots.
```

---

## Community

### Communication Channels

- **GitHub Issues**: Bug reports, feature requests
- **GitHub Discussions**: General questions, ideas
- **Pull Requests**: Code contributions

### Getting Help

1. **Check documentation**: [README.md](README.md), [docs/](docs/)
2. **Search issues**: Someone may have asked before
3. **Ask in discussions**: For general questions
4. **Create issue**: For bugs or feature requests

### Recognition

Contributors are recognized in:
- GitHub contributors list
- Release notes
- Project README (for significant contributions)

---

## Development Tips

### Debugging

```java
// Enable debug logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger logger = LoggerFactory.getLogger(SBCacheMap.class);

logger.debug("Cache get: key={}, value={}", key, value);
```

### Performance Profiling

```bash
# Run with profiler
mvn test -Djava.awt.headless=true -Djmh.profilers=gc
```

### Common Issues

1. **Tests fail randomly**:
   - May be timing-related
   - Add appropriate timeouts
   - Use `Thread.sleep()` cautiously

2. **Memory leaks**:
   - Ensure all resources are closed
   - Check for circular references
   - Use WeakReference when appropriate

3. **Build fails**:
   - Check Java version
   - Clear local Maven repository: `rm -rf ~/.m2/repository`
   - Update dependencies: `mvn clean install -U`

---

## License

By contributing to SB Cached Collection, you agree that your contributions will be licensed under the Apache License 2.0.

---

## Questions?

If you have questions about contributing, please:
1. Check this document thoroughly
2. Search existing issues and discussions
3. Ask in GitHub Discussions
4. Contact maintainers via issue mention

Thank you for contributing to SB Cached Collection! 🎉
