# LLM Documentation

Nucleus provides machine-readable documentation files designed for Large Language Models (LLMs). These plain-text files follow the [llms.txt](https://llmstxt.org/) convention and allow AI assistants to quickly understand the project, its APIs, and configuration options.

## Available Files

| File | Description | Use Case |
|------|-------------|----------|
| [`llms.txt`](../llms.txt) | Concise overview (~100 lines) | Quick context for simple questions |
| [`llms-full.txt`](../llms-full.txt) | Complete documentation (~750 lines) | Full reference for code generation and in-depth tasks |

## Usage

### ChatGPT, Claude, Gemini, etc.

Paste the URL directly in your prompt:

```
Read https://nucleus.kdroidfilter.com/llms-full.txt and help me configure
a Nucleus project with NSIS installer, auto-update, and macOS signing.
```

### Cursor, Windsurf, Claude Code

Add the URL as a documentation source in your AI-powered IDE, or reference it in your project instructions:

```
@doc https://nucleus.kdroidfilter.com/llms-full.txt
```

### Custom Agents / RAG Pipelines

Fetch the files programmatically:

```bash
curl -s https://nucleus.kdroidfilter.com/llms.txt       # concise
curl -s https://nucleus.kdroidfilter.com/llms-full.txt   # complete
```

## What's Included

**`llms.txt`** covers:

- Project overview and key features
- Quick start snippet
- Runtime libraries summary
- Links to all documentation pages
- Migration guide from `org.jetbrains.compose`

**`llms-full.txt`** covers everything above plus:

- Full Gradle DSL reference (all properties and enums)
- Platform-specific configuration (macOS, Windows, Linux)
- Sandboxing pipeline details
- Code signing and notarization (Windows PFX, Azure Trusted Signing, macOS Developer ID)
- Auto-update runtime API with Compose integration example
- Publishing to GitHub Releases and S3
- CI/CD workflows and all composite actions
- All runtime APIs with code examples (executable type, AOT cache, single instance, deep links, decorated window, dark mode detector)
