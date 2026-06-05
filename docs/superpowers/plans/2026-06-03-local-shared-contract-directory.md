# Local Shared Contract Directory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a local shared contract directory that both the Java backend and Python AI project can reference before cross-service development.

**Architecture:** The shared directory lives at `D:\git\outfit-project-contract` and becomes the local source of truth for cross-project business rules, Java/Python boundaries, and the assistant streaming chat contract. The Java and Python repositories keep their own local rules, but their `AGENTS.md` files point agents to the shared contract before assistant or cross-service work.

**Tech Stack:** Markdown, JSON examples, JSON Schema draft 2020-12, local filesystem references.

---

### Task 1: Create Shared Contract Documents

**Files:**
- Create: `D:\git\outfit-project-contract\AGENTS.md`
- Create: `D:\git\outfit-project-contract\docs\business-rules.md`
- Create: `D:\git\outfit-project-contract\docs\coding-boundary.md`
- Create: `D:\git\outfit-project-contract\docs\dev-checklist.md`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\v1.md`

- [ ] **Step 1: Create required directories**

Run:

```powershell
New-Item -ItemType Directory -Force -Path 'D:\git\outfit-project-contract\docs'
New-Item -ItemType Directory -Force -Path 'D:\git\outfit-project-contract\contracts\assistant-streaming-chat'
```

Expected: directories exist.

- [ ] **Step 2: Create shared Markdown files**

Use the approved local shared contract design to create the files listed above. The files must define Java as the source of business truth, Python as the AI generation system, and `contracts\assistant-streaming-chat\v1.md` as the v1 SSE contract.

- [ ] **Step 3: Verify required files exist**

Run:

```powershell
Test-Path -LiteralPath 'D:\git\outfit-project-contract\AGENTS.md'
Test-Path -LiteralPath 'D:\git\outfit-project-contract\contracts\assistant-streaming-chat\v1.md'
```

Expected: both lines return `True`.

### Task 2: Add Contract Examples And Schemas

**Files:**
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\examples\frontend-java-request.json`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\examples\java-python-request.json`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\examples\meta.event`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\examples\token.event`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\examples\done.event`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\examples\error.event`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\schemas\assistant-chat-request.schema.json`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\schemas\python-chat-request.schema.json`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\schemas\sse-meta.schema.json`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\schemas\sse-token.schema.json`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\schemas\sse-done.schema.json`
- Create: `D:\git\outfit-project-contract\contracts\assistant-streaming-chat\schemas\sse-error.schema.json`

- [ ] **Step 1: Create examples and schemas directories**

Run:

```powershell
New-Item -ItemType Directory -Force -Path 'D:\git\outfit-project-contract\contracts\assistant-streaming-chat\examples'
New-Item -ItemType Directory -Force -Path 'D:\git\outfit-project-contract\contracts\assistant-streaming-chat\schemas'
```

Expected: directories exist.

- [ ] **Step 2: Create examples and JSON Schemas**

Create one request example for each request boundary, one `.event` file for each SSE event, and schema files for request and event `data` payload validation.

- [ ] **Step 3: Verify no placeholder text remains**

Run:

```powershell
rg -n "TBD|TODO|FIXME|placeholder|待定" 'D:\git\outfit-project-contract'
```

Expected: no matches.

### Task 3: Point Java And Python Projects To The Shared Contract

**Files:**
- Modify: `D:\git\Intelligent Outfit Recommendation System\AGENTS.md`
- Create: `D:\git\AI Clothing Shopping Assistant System\AGENTS.md`

- [ ] **Step 1: Update Java AGENTS.md**

Add a `Shared Contract` section that points assistant and cross-service development to `..\outfit-project-contract`.

- [ ] **Step 2: Create Python AGENTS.md**

Create an `AGENTS.md` in the Python project that points AI conversation, recommendation, RAG, SSE, and Java-Python API work to `..\outfit-project-contract`.

- [ ] **Step 3: Verify path references**

Run:

```powershell
rg -n "outfit-project-contract|assistant-streaming-chat" 'D:\git\Intelligent Outfit Recommendation System\AGENTS.md' 'D:\git\AI Clothing Shopping Assistant System\AGENTS.md'
```

Expected: both project entry files reference the shared contract.

### Task 4: Final Verification

**Files:**
- Inspect: `D:\git\outfit-project-contract`
- Inspect: `D:\git\Intelligent Outfit Recommendation System\AGENTS.md`
- Inspect: `D:\git\AI Clothing Shopping Assistant System\AGENTS.md`

- [ ] **Step 1: List shared contract files**

Run:

```powershell
Get-ChildItem -LiteralPath 'D:\git\outfit-project-contract' -Recurse -File | Select-Object FullName
```

Expected: shared contract docs, examples, and schemas are listed.

- [ ] **Step 2: Check Java and Python git status**

Run:

```powershell
git -C 'D:\git\Intelligent Outfit Recommendation System' status --short
git -C 'D:\git\AI Clothing Shopping Assistant System' status --short
```

Expected: Java shows the intentional `AGENTS.md` and planning/doc changes plus any pre-existing unrelated changes; Python shows the intentional new `AGENTS.md` plus any pre-existing unrelated changes.

- [ ] **Step 3: Do not run Maven or Python tests**

This task only creates shared Markdown/JSON contract assets and project entry references. Java backend code and Python runtime code are not changed, so Maven and pytest are not required for this step.
