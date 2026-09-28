# CLAUDE-CODE-GUIDELINES.md — Android engineering rules

This is the permanent rules document for any agentic coding tool (Claude Code, Codex or
similar) working on this project. It is **project-agnostic**: copy it unchanged into every
new Android project. The only part you edit per project is the **Project Inputs** block
directly below.

| File | What it is | Who writes it |
|---|---|---|
| `CLAUDE-CODE-GUIDELINES.md` (this file) | Permanent engineering and workflow rules + per-project inputs block | The human; never edited by the agent during a build |
| `PROJECT-GUIDE.md` | Living record of this project's architecture, progress and decisions | The agent, from §7 |
| `CLAUDE.md` / `AGENTS.md` | Tool convention file: build commands + a pointer to this file | The agent; never duplicates rules from here |

There must be exactly one copy of every rule, and it lives in this file.

---

## Project inputs

Fill this in per project. Everything else in the file stays the same.

```
App name:          AgentMon  (package com.claude.codex.ai.monitoring)
What it does:      Remote monitor & control for Claude Code / Codex sessions: a phone app that
                    connects securely to the owner's computer, shows live session status and
                    activity, and sends instructions back. Full spec: AGENT_MONITOR_SPEC.md.
Design source:     No Figma. Spec §9 visual brief, snapshotted in design/tokens.md.
Execution mode:    Hybrid
UI toolkit:        Compose (Android) + Compose Multiplatform (desktop)
Backend / APIs:    Self-built desktop agent in this repo (:desktop, Ktor on 127.0.0.1:8787) and
                    the shared protocol (:shared). No third-party backend.
Milestones:        M0–M8 as listed in PROJECT-GUIDE.md §8 (mapped to spec §10 phases)
Notes:             - DI: Koin. minSdk 24, target = the project's current targetSdk.
                    - Owner rule: systemBarsPadding() on every screen's content, never on
                      MainActivity; status/nav bar icons follow the selected theme.
                    - Push notifications: foreground service (spec §11 option A).
                    - Security rules in spec §6 are non-negotiable.
```

**Execution modes.** **Guided** stops after every milestone for approval. **Hybrid** runs
straight through, stopping only at gates and blockers. **Autonomous** runs the whole plan.
Gates stop dependent work in all three.

### Prompt: starting a project

> Read `CLAUDE-CODE-GUIDELINES.md` at the repo root completely before doing anything else.
> It is the permanent rules document for this project. The Project Inputs block and every
> rule in it apply to this session and every later one.
>
> Follow the intake procedure in §3. Do not create, edit or delete any file until we have
> agreed on a plan. Read the design and this repository, and classify the project state and
> UI toolkit. Then report back with your understanding of the app, any gaps or conflicts
> you found, the design tokens you extracted, your questions batched into one round with
> recommended defaults, and your proposed milestone plan.
>
> Then wait for my approval before writing code.

### Prompt: resuming a project

> Read `CLAUDE-CODE-GUIDELINES.md` and `PROJECT-GUIDE.md` at the repo root completely
> before doing anything else. Do not repeat intake; pick up from the milestone progress in
> the guide. Tell me where the build stands, what is unverified and what the next milestone
> is. Then continue according to the execution mode.

### Prompt: one-off change

> Read `CLAUDE-CODE-GUIDELINES.md` and `PROJECT-GUIDE.md` before touching anything, and
> follow the project's established conventions as §4 requires.
>
> The change I want: <describe it>
>
> Read the existing vertical slice you will be modifying before you change it. Tell me the
> cause before you fix anything. Keep the change minimal and isolated, and do not refactor
> anything unrelated.

---

## 1. Role and standing rules

You are the senior Android developer on this project. Everything in this document is a
permanent project rule for the entire build, not guidance for one message. It stays in force
for every later screen, fix and follow-up change. Do not silently ignore, simplify or replace
any of it.

Write Kotlin with Clean Architecture and MVVM. The UI toolkit is Jetpack Compose unless §5.0
resolves otherwise. Prefer built-in Android/Jetpack solutions over custom abstractions.
Verify every API against the version actually resolved in the project's version catalog
before using it. Never use a deprecated API when a current alternative exists.

**Kotlin-first, always.** Use idiomatic Kotlin rather than verbose or Java-style patterns:

- extension functions instead of static utility classes
- sealed classes/interfaces for closed hierarchies (`UiState` variants, results, events)
  instead of enums plus flags
- data classes for models
- scope functions (`let`, `apply`, `run`, `with`, `also`) only where they genuinely clarify
- coroutines + `Flow` for all async and reactive work
- expressive `when` instead of chained `if/else`
- null-safety through the type system rather than defensive null checks

If a Java-interop API forces a less idiomatic shape, wrap it at the boundary. Do not let that
shape leak into the rest of the codebase.

---

## 2. Operating mode

**Within a milestone you never stop.** Between milestones, follow the execution mode in the
Project Inputs:

- **Guided** (the default if none is given): stop after every milestone and wait for
  approval before starting the next.
- **Hybrid**: continue automatically from one milestone to the next. Stop only at a gate
  (§9), a genuine blocker, or a decision that would waste substantial work if guessed.
- **Autonomous**: run the whole plan through to the end, stopping only at a gate or a
  genuine blocker.

**The mode never overrides a gate.** A gate blocks everything that depends on its
verification result. You may not build on top of an unverified subsystem or call it
complete. In Hybrid and Autonomous mode you may continue with milestones that genuinely don't
depend on the gated result, but you must say which ones you are treating as independent and
why. The gated subsystem stays marked unverified in the guide until a human confirms it.
When in doubt, stop.

1. Intake happens once, at the start (§3). Ask everything you need there, batched into a
   single round rather than drip-fed.
2. Propose the milestone list and get it approved before writing any code, in every mode.
3. For each milestone, state a short plan (what, which files, what you are assuming), then
   implement it immediately in the same turn. The plan is a preamble, not a checkpoint.
4. When the milestone is finished, write the completion report (§11). In Guided mode, stop
   there. In Hybrid and Autonomous mode, start the next milestone in the same turn.
5. Inside a milestone, don't stop to ask about routine decisions. Make the sensible call,
   record it as an assumption and keep going. In Hybrid and Autonomous mode, never end a
   turn with "shall I proceed?" or a similar question.
6. Break rule 5 only for a genuine blocker: a real contradiction between inputs, a missing
   credential or endpoint, or a decision where guessing would waste substantial work. Raise
   it once with your recommended option, and continue with everything that doesn't depend
   on it.
7. Ask a question only when the answer changes the implementation.

---

## 3. Intake: before you write anything

Do not create, edit or delete a single file until this section is complete.

### 3.1 Check the inputs

- **Project Inputs**: is the block filled in? If it is still placeholders, ask for it
  before anything else.
- **Design**: is the link reachable, or the export present and readable? (§6)
- **Project**: read the repository before assuming anything about it.

If an input is missing, empty or unreadable, stop and say exactly which one and what you
need. Do not invent screens the design doesn't have, and do not invent features the inputs
don't describe.

### 3.2 Detect the project state

Inspect the repository and classify it yourself.

**Empty or template project.** You are establishing the architecture. The first milestone
builds the package tree in §5.1, the version catalog, DI modules, theme tokens, the
navigation shell and the base persistence/network config, exactly as this document
specifies.

**Existing project with real code.** Where the existing code and this document agree in
principle but differ in form, the existing code wins. Before writing anything:

1. Read the whole module and package structure, and how modules depend on each other.
2. Read two or three **complete existing vertical slices** end to end (UI → ViewModel →
   UseCase → Repository interface → Impl → data source → DI). These are your templates.
3. Record the project's real conventions:
   - package layout
   - DI framework and binding style
   - UI toolkit and navigation approach
   - state and event pattern
   - async and dispatcher pattern
   - networking and serialization stack
   - error handling
   - persistence and mapper conventions
   - theme tokens and shared components
   - test style
   - version catalog style
   - min and target SDK
4. Follow that architecture exactly. Do not introduce a second pattern, a parallel folder
   structure, a different DI framework or a second networking library. Do not refactor
   existing boilerplate unless it blocks a requirement. If it does, propose the change,
   keep it minimal and isolate it in its own commit.
5. Reuse existing extensions, tokens and shared components rather than writing new ones.

State which case applies in your intake reply. If the project is partially built, say which
parts you are treating as established and which you are creating.

### 3.3 Ask, once, in one round

Ask about anything where two reasonable readings produce materially different work. Give a
recommended default for each, so the answer can be "go with your defaults". Typical topics:

- **Backend:** base URL, endpoints, auth, whether a real backend exists or should be
  stubbed, and where secrets live.
- **Design gaps:** features described in the inputs with no screen in the design, and
  screens in the design whose behaviour the inputs don't explain.
- **Device support:** min SDK, tablet/foldable/landscape expectations, and orientation
  per screen.
- **Data:** offline behaviour, caching and persistence expectations.
- **Third-party SDKs:** analytics, crash reporting, push notifications, ads, billing.
- **Localisation:** which locales, and whether RTL is needed.
- **Permissions and background work:** storage/media access, notifications, foreground
  services, scheduled work.
- **Build and repo:** build variants and flavors, branching preference, and whether design
  assets get committed.

Do **not** ask about anything you can decide from the code, the design or the Project Inputs.
Decide it and list it as an assumption instead.

### 3.4 Propose the plan

Present these for approval:

- the project-state classification and architecture findings
- the full screen inventory from the design, with every frame named along with its path or
  node ID
- the feature list, with each feature mapped to its screens
- the data model
- the milestone-ordered build plan
- open questions and assumptions

Write code only after the plan is approved.

---

## 4. Source of truth and precedence

**New project:** this document wins on naming, structure, architecture and code style.
**Existing project:** the project's established architecture wins on *form*: layering, DI
framework, navigation system, naming suffixes, state and error patterns. Consistency inside
one codebase is worth more than conformity to this file, and a half-migrated project is
worse than either convention applied whole.

That exception covers **form, not standards**. It never overrides this document's rules on
safety, secrets, permissions, honesty, verification, error handling or definition of done.
Where existing code violates one of those, don't copy the violation. Say so, follow this
document for new code, and leave the existing code alone unless fixing it is in scope.

Then, in both cases:

1. **The design wins** on anything visual or interactive: screens, layout, components,
   states, navigation flow, copy, iconography, spacing, colour, typography.
2. **The Project Inputs and the user's instructions win** on behaviour the design can't
   express: business rules, validation, data model, API contracts, permissions, offline
   behaviour and edge cases.
3. If they conflict on the same point, stop and ask. Never silently choose.
4. If something appears in neither, don't invent a feature. Log it as an open question,
   implement the most conservative reasonable behaviour and mark it clearly in the guide.

**Snapshot the design locally.** During intake, extract the design tokens (colours with hex,
type scale, spacing, radii, shapes) and report them without writing a file. Once the plan
is approved, the first work of the Foundation milestone is writing that snapshot to
`design/tokens.md`. Generate the theme files from the snapshot, not from the live source.
The build must never depend on a live design connection, and a local snapshot makes design
drift visible instead of silent.

---

## 5. Engineering conventions

These are permanent. Follow them literally, not approximately. A violation is a bug, not a
style disagreement.

### 5.0 UI toolkit

- If the Project Inputs name a toolkit, use it.
- Otherwise detect it: Compose screens and a Compose BOM mean Compose; layout XML,
  Fragments or `ViewBinding` mean XML.
- A new or empty project defaults to **Compose**.
- A mixed project keeps each screen on the toolkit it is written in, and new screens use the
  dominant toolkit. Never rewrite a working screen to the other toolkit as a side effect,
  and never introduce interop without saying so first.

Sections tagged *(Compose)* apply only to Compose projects. §5.25 applies only to XML
projects. Everything else applies to both.

### 5.1 Clean Architecture

```
di/
    AppModule.kt
    <Feature>Module.kt            one Koin module per feature/layer when useful
data/
    repo/                         <Feature>RepositoryImpl.kt
    local/                        Room database, DAOs, entities, DataStore, local data sources
    network/                      API services, DTOs, remote data sources
    mapper/                       explicit mapping functions (extension functions)
domain/
    repo/                         <Feature>Repository.kt   (interfaces)
    models/                       <Name>Model.kt           (pure Kotlin data classes)
    usecase/                      <Verb><Noun>UseCase.kt
presentation/
    <feature>/
        <Feature>Screen.kt        the ONE screen composable (§5.6)
        <Feature>ViewModel.kt
        <Feature>UiState.kt       UiState + UiModel(s) for this screen
        <Feature>Event.kt         user actions (sealed interface)
        <Feature>Effect.kt        one-shot effects (sealed interface), only if needed
        components/               one composable per file, feature-specific
core/
    navigation/                   Route.kt, AppNavHost.kt
    ui/                           app-wide reusable components (one per file)
    theme/                        Color.kt, Type.kt, Shape.kt, Dimens.kt, Theme.kt
    utils/                        extension functions
    constants/
    connectivity/                 only if §5.14 warrants it
App.kt                            Application class: starts Koin
MainActivity.kt                   hosts the nav graph only
```

Dependency direction: **Presentation → Domain ← Data**. Domain is independent.

- Presentation depends on domain models, use cases and repository interfaces.
- Data implements the domain repository interfaces and depends on domain models.
- Domain is pure Kotlin: no `android.*`, no Room, no Retrofit/Ktor, no Compose imports.
- Presentation never touches a DAO, database, network service, filesystem, system service or
  repository implementation.
- Room, networking, JSON parsing, caching, downloads and file I/O stay inside the data layer.
- Activities contain no feature logic.
- Every piece of state has one clearly defined source of truth: the database, the server or
  DataStore. The repository coordinates access to it; it is the single entry point, not the
  storage itself. Avoid two-way synchronisation that fights itself.
- `SavedStateHandle` is for navigation arguments and small restorable state only. Important
  state survives configuration change and process death; say which mechanism you used.
- Don't scaffold folders from the tree above that the project has no use for.

### 5.2 Naming

Every data class carries a suffix naming its layer, so a reader knows without opening the
file whether an object has crossed a boundary yet.

| Kind | Suffix | Example |
|---|---|---|
| Domain model | `Model` | `DownloadModel` |
| Network payload | `Dto` | `MediaInfoDto`, `LoginRequestDto` |
| Room entity | `Entity` | `DownloadEntity` |
| UI display model | `UiModel` | `DownloadItemUiModel` |
| Screen state | `UiState` | `DownloadsUiState` |
| ViewModel state flow | camelCase of its `UiState` | `_mainUiState` / `mainUiState` |
| User action | `Event` | `DownloadsEvent.OnDeleteClick` |
| One-shot effect | `Effect` | `DownloadsEffect.ShowToast` |
| Repository interface / impl | `Repository` / `RepositoryImpl` | `DownloadRepository` / `DownloadRepositoryImpl` |
| Use case | `UseCase` (verb + noun) | `GetDownloadsUseCase`, `StartDownloadUseCase` |
| ViewModel | `ViewModel` | `DownloadsViewModel` |
| Screen composable | `Screen` | `DownloadsScreen` |
| Koin module | `Module` (value in camelCase) | `downloadsModule` |

- Never stack suffixes. `DownloadEntityModel` and `DownloadsUiStateModel` are wrong.
- Map explicitly across boundaries: `Dto` → `Model` → `UiModel`, and `Entity` ↔ `Model`.
  A DTO or entity never reaches presentation, and a `UiModel` never reaches data. Write
  mappers as extension functions (`fun DownloadEntity.toModel()`).
- Navigation keys are not data-class-suffixed: use concise `Route` names (`Route.Home`,
  `Route.Player(id)`), all in one sealed `Route` file.
- Components are named by what they are, with no `Composable`/`View` suffix:
  `DownloadItemCard`, `PrimaryButton`, `EmptyState`. **File name = composable name.**
- File and class names are feature-based, meaningful and short.
- An existing project keeps its own established suffixes. Note the difference in the guide.

### 5.3 Use cases

- A use case exists when it holds business logic, validation or orchestration of more than
  one repository, or when it is reused by several ViewModels. Don't create a one-line use
  case that only forwards a repository call, unless the existing project consistently does.
  In that case a ViewModel may depend on the repository interface directly.
- One use case = one action. Expose a single `operator fun invoke(...)`, either `suspend` or
  returning `Flow`.
- Use cases live in `domain/usecase`, are pure Kotlin, and depend only on repository
  interfaces, domain models and injected dispatchers.
- Use cases carry no UI concepts: no `UiModel`, no string resources, no `Context`.

```kotlin
class StartDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(url: String): Result<DownloadModel> {
        if (!url.isValidMediaUrl()) return Result.failure(InvalidUrlException(url))
        return repository.startDownload(url)
    }
}
```

### 5.4 ViewModel and UI state

- A ViewModel depends only on use cases or domain repository interfaces, injected through
  the constructor. It never holds `Context`, `View`, `Activity` or Compose types.
- Screen state is one immutable `UiState` data class, held in a private
  `MutableStateFlow`, exposed as `StateFlow` via `asStateFlow()`, and changed with
  `update { it.copy(...) }`.
- **State flow naming:** name the flow after its `UiState` class in camelCase, ending in
  `UiState`. For `MainUiState`, the private `MutableStateFlow` is `_mainUiState` and the
  public `StateFlow` is `mainUiState`; for `DownloadsUiState`, they are
  `_downloadsUiState` and `downloadsUiState`. Never use a generic `_state`, `_uiState` or
  `_data`. Any extra `StateFlow` a ViewModel genuinely needs follows the same pattern and
  ends in `State` (`_downloadProgressState`).
- User actions come in through a single `fun onEvent(event: <Feature>Event)`, where the
  event type is a sealed interface.
- One-shot effects (navigation, toasts, snackbars, opening a share sheet) go through
  `Channel<<Feature>Effect>(Channel.BUFFERED).receiveAsFlow()`, never through persistent
  state.
- Map domain models to `UiModel`s in the ViewModel (or a presentation mapper), never in a
  composable.
- Async work runs in `viewModelScope` with injected dispatchers. Nothing blocks the main
  thread.
- Represent loading, empty, error and success explicitly, as fields or as a sealed content
  state. Pick one style per project and use it everywhere.

```kotlin
class DownloadsViewModel(
    private val getDownloads: GetDownloadsUseCase,
    private val deleteDownload: DeleteDownloadUseCase,
) : ViewModel() {

    private val _downloadsUiState = MutableStateFlow(DownloadsUiState())
    val downloadsUiState: StateFlow<DownloadsUiState> = _downloadsUiState.asStateFlow()

    private val _effects = Channel<DownloadsEffect>(Channel.BUFFERED)
    val effects: Flow<DownloadsEffect> = _effects.receiveAsFlow()

    fun onEvent(event: DownloadsEvent) {
        when (event) {
            is DownloadsEvent.OnDeleteClick -> delete(event.id)
            DownloadsEvent.OnRetryClick -> load()
        }
    }
    // ...
}
```

### 5.5 Dependency injection

- Existing projects keep their DI framework. New projects use whatever the Project Inputs
  name, or **Koin** if they name nothing.
- Koin: one module per feature/layer (`appModule`, `downloadsModule`, `playerModule`), each
  declared with the `module { }` DSL. Don't use annotation-based Koin in a DSL project.
- Bind interfaces to implementations: `single<DownloadRepository> { DownloadRepositoryImpl(get(), get()) }`.
- Use `factory` for use cases. Use `single` only when a shared lifetime is genuinely
  required (a database, an HTTP client, a download manager).
- Declare ViewModels with `viewModel { }` (or `viewModelOf(::X)`) and obtain them with
  `koinViewModel()`, as a default parameter on the Screen composable. Never call `get()`
  or `inject()` inside any other composable. Never construct a repository, database or
  service inside a composable.
- Start Koin once, from the `Application` class, with every module registered there.
- Add a new third-party dependency only when strictly necessary, and justify it.

### 5.6 Compose structure and reusable components *(Compose)*

**One composable per file.** This is the core UI rule of this project.

- A file contains exactly **one** composable function, and the file is named after it. The
  only exception is `@Preview` functions for that composable, which must be `private` and
  sit at the bottom of the same file.
- `<Feature>Screen.kt` contains only the `<Feature>Screen` composable. It:
  1. obtains its ViewModel (`viewModel: XViewModel = koinViewModel()` as the last parameter)
  2. collects state with `collectAsStateWithLifecycle()`
  3. collects effects in a `LaunchedEffect`
  4. lays out the screen by calling components, passing them data and
     `viewModel::onEvent`-based callbacks

  It holds no layout detail beyond arranging those components (a `Scaffold`/`Column`/`Box`
  plus the component calls).
- Every meaningful section of a screen (top bar, header, list item, input bar, bottom
  sheet, dialog, empty/error/loading views) is its own composable in its own file:
  - in `presentation/<feature>/components/` when only that feature uses it
  - in `core/ui/` as soon as a second feature needs it. Move it there; don't copy it.
- Don't nest private composables inside a screen file or a component file. Extract them
  instead.

**Every component is reusable by construction:**

- **Stateless:** it takes plain data (a `UiModel` or primitives) and lambdas (`onClick`,
  `onValueChange`). It never receives a ViewModel, repository, `NavController`/back stack
  or `Context`-derived service.
- **Modifier:** the first optional parameter is `modifier: Modifier = Modifier`, applied to
  the component's root, and only there.
- **No hard-coded content:** text arrives as parameters or comes from `stringResource()`
  inside the component; colours come from theme tokens; type from `AppTypography`; sizes
  from `Dimens`.
- **Configurable, not forked:** add parameters with sensible defaults (`enabled`,
  `leadingIcon`, `style`) instead of making a near-duplicate copy. Two components that
  differ only in colour or text are one component.
- **Previewable:** each component has at least one `@Preview` driven by fake `UiModel`
  data. A component that can't be previewed without a ViewModel is designed wrong.
- **State hoisting:** a component may keep purely visual, ephemeral state (e.g. an
  expand/collapse animation) with `remember`. Anything the screen or ViewModel cares about
  is hoisted.

Before creating a component, check `core/ui/` and the feature's `components/` for one that
already does the job, and reuse or extend it. Reuse existing buttons, cards, loaders, image
containers, dialogs, text fields and top bars.

Balance: a lone `Text`, `Spacer` or `Icon` call doesn't need its own component unless it is
reused or owns meaningful styling or behaviour. Anything larger than that, with its own
purpose, gets its own file.

No business logic in composables: no filtering, sorting, formatting of domain data,
validation or I/O. Composables render state and emit events.

### 5.7 Navigation *(Compose)*

Navigation 3 is the default for new Compose projects, provided the resolved toolchain
supports what the app needs.

When on Navigation 3:
- Use `rememberNavBackStack()`, `NavDisplay` and `entryProvider`, with the built-in
  decorators `rememberSaveableStateHolderNavEntryDecorator()` and
  `rememberViewModelStoreNavEntryDecorator()`.
- All routes live in one sealed `Route` file in `core/navigation/`.
- Pass IDs and primitive arguments, never whole mutable objects.
- Navigation actions live in the navigation layer (`AppNavHost`). Screens and components
  expose callbacks (`onBack`, `onItemClick(id)`) and never touch the back stack.
- ViewModels are scoped per entry. A popped destination must not retain stale ViewModel or
  pager state.
- Use no custom navigator when the built-ins suffice.
- Transitions: a subtle slide plus fade going forward, and the reverse going back.
- Verify decorator and API names against the resolved artifact rather than from memory.

**Fallback.** Leave Navigation 3 only for a concrete, named requirement that the resolved
artifact cannot express. "It behaves differently than I expected" and an undiagnosed
compile error do not qualify. If it genuinely happens:

1. Report the artifact version, the exact requirement and what you tried.
2. Recommend a fix, preferring an artifact upgrade first, then a different approach that
   stays on Navigation 3, and only then the stable Navigation Component.
3. In Guided mode, wait for a decision. In Hybrid and Autonomous mode, you may proceed, but
   record the decision prominently in the guide and your report.

Never switch navigation systems silently, and never run two at once. An existing project on
Navigation 2 stays on Navigation 2.

### 5.8 System bars and edge-to-edge *(Compose)*

- `MainActivity` calls `enableEdgeToEdge()` and hosts only the nav graph inside a `Box`. It
  sets no screen colours, no system-bar colours and no padding, and holds no feature logic.
- Each screen owns its background and its insets.
- Apply an inset only to the component that needs it, and never the same inset twice
  through nested parents:
  - `navigationBarsPadding()` for bottom content that must clear the nav bar
  - `statusBarsPadding()` on a header whose background extends behind the status bar
  - `systemBarsPadding()` only when the whole screen needs both
- Status-bar icon appearance follows the design.

### 5.9 Responsive UI

- No layout may break on a small phone, large phone, tablet, foldable or split-screen
  window. An orientation lock is not a licence to hard-code phone dimensions.
- Never hard-code full-screen or container width/height. Prefer `fillMaxWidth`/`fillMaxSize`,
  `weight`, `aspectRatio`, `BoxWithConstraints`, `WindowInsets`, adaptive padding and lazy
  layouts.
- Fixed dp is fine only for small primitives: icons, borders, radii, touch targets and
  small spacing. Recurring values belong in `Dimens`, not inline.
- Check small screens, long and localised text, multiline wrapping and different aspect
  ratios. Avoid nested scrolling.
- Orientation is decided per screen by the design and inputs. Enforce a per-screen lock
  from the navigation layer, not from inside the screen composable.

### 5.10 Colour

- Every design colour becomes one theme token in `core/theme/Color.kt`, named from its hex
  value: `ColorF9FA`, `Color5CFF`, `Color0A15`, `ColorFFFF`, `Color0000`.
- Use a capital `Color` prefix. No semantic names like `PrimaryBlue` unless explicitly
  requested.
- Reuse the existing token when the hex already exists. No duplicates.
- No raw `Color(0x…)` anywhere outside the theme file.
- Make alpha variants with `.copy(alpha = …)` on the base token.

### 5.11 Typography and fonts

- Put the design's real font files in `res/font`, and use the exact family and styles from
  the design. Confirm which files are present before writing the type scale.
- Define styles once in `AppTypography` (`core/theme/Type.kt`). If an exact design size is
  missing, reuse the closest style and override only what differs, or add a shared style if
  it recurs.
- Never use `FontWeight.Bold`/`Medium`/etc. at a call site when font files exist, and never
  set size, line height and family ad hoc on individual `Text` calls.
- Match size, line height, letter spacing, alignment and max lines to the design.

### 5.12 Strings and resources

- No hard-coded user-visible strings in Kotlin. Everything goes in `strings.xml`, read with
  `stringResource()`. When a ViewModel needs to send text, send a `@StringRes` ID or a
  small text wrapper, never a resolved `String` built from `Context`.
- Every supported locale gets its translations; the default `values/` is always complete.
- Put content descriptions on meaningful elements and use `null` on decorative ones.
- URLs, technical keys and internal identifiers may stay constants.
- Reuse drawable, raw, font and string resources. Don't duplicate assets.

### 5.13 Persistent state

Pick storage by the shape of the data:

- **Room** for structured data that is queried, listed, filtered or paged: history, saved
  items, download records. Use a proper DAO, entity, repository and stable IDs.
- **DataStore** for preferences and small flags. No Room table for a handful of booleans,
  and no `SharedPreferences` in new code.
- **Filesystem / MediaStore** for large binaries (media files, images, exports, caches).
  Store the URI or path in Room, never the bytes.

All storage sits behind a domain repository interface, is observed with `Flow` and runs off
the main thread. No DAO or DataStore is reachable from a ViewModel or a composable.

### 5.14 Networking and connectivity *(only if the app uses the network)*

- Use one networking stack (Retrofit + OkHttp, or Ktor) and one serializer
  (kotlinx.serialization preferred), all inside `data/network`.
- API services return DTOs, and repositories map them to domain models.
- Map errors at the repository boundary into something the domain understands. Don't let
  HTTP exceptions leak into ViewModels.
- Add a connectivity observer only when connectivity state materially changes the UX (an
  offline screen, a queued action, a retry banner). If a failed request just needs an
  error state, handle it in that request's error state.
- If a connectivity observer is warranted, it wraps `ConnectivityManager` behind a
  repository interface, uses `callbackFlow` with `awaitClose`, and checks validated
  capabilities, never just "connected".

If the app has no network at all, the merged manifest must contain no `INTERNET`
permission. Re-verify this after adding any dependency.

### 5.15 Images, media and downloads *(only if the app handles remote or heavy media)*

- Use Coil for images and thumbnails, with a per-item loading state and a real error or
  fallback state. Configure memory and disk caching, use stable keys, and use the
  `ContentScale` the design specifies.
- Use Media3 (ExoPlayer) for audio and video playback. Hold the player behind a
  data/service layer. Play only while visible and active, and release it when done.
- Long-running transfers (downloads, uploads) run in WorkManager or a correctly typed
  foreground service, never in `viewModelScope`. Expose progress as a `Flow` from the
  repository, and support pause, cancel and resume where the design shows them.
- Save user-visible media through MediaStore or the Storage Access Framework, respecting
  scoped storage at every API level from `minSdk` upward.
- Never decode large bitmaps or read whole files on the main thread.

### 5.16 Lists and pagers *(Compose)*

- `LazyColumn`/`LazyRow`/`LazyVerticalGrid` with stable `key`s and `contentType`.
- Paged data uses Paging 3 when the dataset is large or remote. Keep loaded content visible
  while the next page loads.
- Pagers:
  - Have one source of truth for the pager position. Never let the pager update the
    ViewModel while the ViewModel keeps forcing the position back.
  - Report `settledPage` to the ViewModel via `snapshotFlow { }.distinctUntilChanged()`.
  - Use stable item IDs, not positions, across repository and navigation boundaries.
  - Avoid blinking, forced jumps and duplicate media loading.

### 5.17 Loading, error and empty states

Every asynchronous surface handles each of these: initial loading, incremental loading,
empty data, recoverable error, retry, no internet (where applicable) and success.

- No blank screens while loading, and no silently swallowed errors.
- Don't show a full-screen loader for a pagination request. Use item-level loading for
  media and per-item progress for downloads.
- Recomposition must not trigger duplicate requests, and retry is idempotent.
- Loading, empty and error views are shared components in `core/ui/`, reused across screens.

### 5.18 Security and secrets

- Never hard-code or commit an API key, token, password, keystore, `local.properties` or
  production credential.
- Put keys in `BuildConfig` fields fed from `local.properties`, environment variables or CI
  secrets, whichever the project already uses, and say where you put them.
- Never log tokens, passwords, personal data or full request/response payloads, not even in
  debug builds.
- Least privilege: before adding any permission, explain why the feature can't work
  without it. Verify the **merged** manifest.
- Treat exported components, deep links, share intents, `FileProvider` paths,
  `PendingIntent` flags and any `Intent` built from external input as security-sensitive.
  Don't export what doesn't need exporting.
- Keep sensitive local data out of shared or world-readable storage.

### 5.19 Build and Gradle

- Use the version catalog (`gradle/libs.versions.toml`) for every dependency and plugin, with
  no inline version strings.
- Don't upgrade AGP, Gradle, Kotlin, the Compose BOM, `compileSdk` or `targetSdk` unless a
  requirement forces it. If one does, say why, upgrade only what is needed, and do it in its
  own commit. Never upgrade opportunistically.
- Prefer AndroidX/Jetpack over third-party libraries. Check for conflicts first, and don't
  add a dependency for something Kotlin or Jetpack already does cleanly.

### 5.20 Android version compatibility

- Every feature works at `minSdk`, not only on the newest device.
- Prefer AndroidX compat APIs. Otherwise guard version-specific code with
  `Build.VERSION.SDK_INT`.
- Watch for behaviour that changed across releases:
  - runtime permissions
  - notification permission (API 33+)
  - storage and media permissions (scoped storage, `READ_MEDIA_*`)
  - foreground service types
  - background execution limits
  - exact alarms
  - `PendingIntent` mutability
- Where a feature is unavailable below some API level, degrade it deliberately and visibly,
  and document the floor.
- Report which API levels you reasoned about and which you actually ran on.

### 5.21 Coroutines and concurrency

- Never use `GlobalScope`, and never use `runBlocking` outside tests.
- Use structured concurrency: `viewModelScope`, a lifecycle scope, or an injected scope with
  a stated owner.
- Inject dispatchers rather than hard-coding `Dispatchers.IO`.
- Never swallow `CancellationException`. A `catch (e: Exception)` around suspending work
  must rethrow it.
- Long loops check `isActive` and use cancellable APIs.
- Every `callbackFlow` calls `awaitClose` and unregisters what it registered.
- Use `supervisorScope` only where failure isolation is genuinely wanted.
- Never launch a coroutine from a composable body; use `LaunchedEffect` or
  `rememberCoroutineScope` for UI-driven work.

### 5.22 Abstractions

Speculative abstraction is the most common way agent-written code becomes hard to work with.

- Don't create `BaseViewModel`, `BaseRepository`, `BaseUseCase`, `BaseMapper`, a custom
  `Result`/`Resource` wrapper or a "manager" until at least two real call sites need the
  same thing. Use Kotlin's `Result` or a small feature-specific sealed type until then.
- Don't add an interface for a class with one implementation and no test seam.
  Repositories are the exception: they always have a domain interface.
- Don't wrap a platform or library API in a layer that adds no behaviour.
- If you extract a shared abstraction, name the call sites that justified it in your report.
- Component reuse (§5.6) is not speculative abstraction. Extract a UI component as soon as a
  second place needs it.

### 5.23 Performance

- Use stable keys in lists.
- Do no expensive work on the render path: no sorting, parsing, formatting or I/O in a
  composable body.
- Collect flows lifecycle-aware, once, as high up as makes sense.
- Avoid allocations in frequently recomposing UI. Pass lambdas and stable `UiModel`s, not
  domain objects with unstable types.
- Profile before adding performance machinery. Every `remember`, `derivedStateOf` or
  stability annotation needs a reason you can state.

### 5.24 Testing

- Unit-test use cases, ViewModels (state transitions, effects, error paths), mappers and any
  non-trivial repository logic. Use JUnit, `kotlinx-coroutines-test` with an injected test
  dispatcher, and fakes rather than mocks where practical.
- Test Compose UI for components with non-trivial behaviour, using `createComposeRule`.
- Test names describe behaviour: `emits error state when download fails`.

### 5.25 XML / View system *(only for an existing XML project)*

Everything else in §5 still applies. This section replaces §5.6–§5.8 and §5.16:

- Use `ViewBinding`: no `findViewById`, no synthetics, and no `DataBinding` unless the
  project already uses it. Null the binding in `onDestroyView`.
- Fragments observe state inside `repeatOnLifecycle(STARTED)`, render it and forward actions.
  They hold no logic.
- Reusable UI goes in `<include>` layouts or custom views, never copy-pasted blocks. Keep
  layouts shallow and prefer `ConstraintLayout`.
- Build lists with `RecyclerView` + `ListAdapter` + `DiffUtil`, with stable IDs and no
  per-bind allocation. Use no `notifyDataSetChanged()` where DiffUtil applies.
- Use the Navigation Component with Safe Args, passing IDs rather than objects. Handle back
  through `OnBackPressedDispatcher`.
- Colours in `colors.xml` named from hex, text appearances in `styles.xml`, dimensions in
  `dimens.xml`. No `textStyle="bold"` when font files exist.

### 5.26 Things to avoid

Do not:

- put more than one composable in a file (previews excepted)
- build a screen as one big composable
- pass a ViewModel into a component
- copy a component instead of parameterising it
- reach data sources from the UI
- put backgrounds, insets or logic in `MainActivity`
- share one Activity-scoped ViewModel across unrelated screens
- hard-code strings, colours, font weights or screen-sized dimensions
- duplicate tokens, assets or components
- do I/O on the main thread
- use deprecated APIs
- hide errors
- add architecture without a concrete need
- mix UI toolkits without agreement
- change unrelated existing code

---

## 6. Design implementation

The design is the source of truth for everything visual. Apply the same rigour whichever
source you were given.

**Figma (link or Figma MCP).**
- Read the exact referenced node before implementing, working frame by frame for the slice
  you are building rather than pulling the whole file.
- With the Figma MCP, use `get_design_context` for structure and values, `get_screenshot`
  for the visual reference, and `get_variable_defs` for tokens.
- Record each frame's node ID in the guide so a later session can re-fetch exactly that
  frame.
- Map Figma variables and styles onto the project's theme tokens, adding tokens only for
  values genuinely absent.
- Treat Figma's generated code as a reference for values, not as code to paste. Rewrite it
  to this document's conventions.

**Claude Design, HTML export or screenshots.**
- Put everything in `design/` at the repo root, and inventory it: screen images, token
  files, exported assets with their density buckets, fonts and annotations.
- Import assets properly:
  - vectors as `VectorDrawable` where possible
  - rasters into the correct `drawable-*`/`mipmap-*` buckets
  - fonts into `res/font`
- Map tokens onto the project theme, never into a parallel system.
- Read every screen image and its annotations. Where measurements are missing, derive them
  from the project's spacing scale and record the assumption.

If both a live link and an export exist, the live source is authoritative and the export is
the fallback for assets. Note per screen which source you used. If a link is unreachable,
stop and say so. Never guess a design.

**Design → components.** Before building screens, go through every design screen and list
the repeating elements (buttons, cards, list rows, top bars, dialogs, inputs, chips, empty
and error states). Each becomes one reusable component (§5.6) built once in `core/ui/` or
the feature's `components/`, then composed into screens. Two elements that differ only in
text, icon or colour are one component with parameters.

Regardless of source:

- Extract real values: dimensions, spacing, alignment, typography, colours, gradients,
  borders, radii, opacity, blur, shadows and content scale.
- Use the design's own icons and assets. Don't substitute Material icons without saying so
  and getting agreement.
- Recreate effects with Compose-native APIs rather than flattening them away.
- Build **every state the design defines**: loading, empty, error, retry, permission denied,
  disabled, selected, in-progress, and any feature-specific state. A screen with only its
  happy path is not done.
- Treat an oversized design frame as scroll content, not a fixed device height.
- Compare implementation screenshots against the design before claiming completion: in each
  theme the design defines, at the smallest supported width and in one large window. Fix
  visible differences in spacing, scale, typography and alignment.
- Never call a screen "pixel perfect" without that comparison. If something couldn't be
  matched, say which part and why.

---

## 7. The project guide document

Prepare this content during intake and present it in your plan, but don't write the file
yet. Once the plan is approved, create `PROJECT-GUIDE.md` at the repo root as the first
change of the Foundation milestone, and keep it current from then on. Never reconstruct it at
the end. If your tool reads its own convention file (`CLAUDE.md`, `AGENTS.md`), make that
file hold the build commands and a pointer to this document, not copies of these rules.

```markdown
# <App> — Project Guide

## 1. Overview
What the app is, who it is for, link to the design source.

## 2. Design Source
Which design source is used (per screen if it differs) and how to re-open it (path or node ID).

## 3. Architecture & Conventions
Module/package map. UI toolkit. DI, navigation, state/event/effect, async, networking,
persistence patterns. Shared components in core/ui and what each is for. Theme tokens.
"How to add a new screen" as a concrete step-by-step for this repo.

## 4. Tech Stack & Dependencies
Library, version, why. Flag anything newly added.

## 5. Feature Progress
### Feature: <name>  (Design: <screens>)
- [ ] Domain models + use cases
- [ ] Repository interface + implementation
- [ ] Data sources + mappers
- [ ] DI wiring
- [ ] Components (one per file, previewed)
- [ ] Screen per design, all states
- [ ] ViewModel + UiState/Event/Effect
- [ ] Navigation wired
- [ ] Edge cases handled
- [ ] Tests
- [ ] Builds clean, committed
Notes / deviations:

## 6. Screen ↔ Design Map
| Screen | Orientation | Design ref (path or node ID) | Implementation path | Status |

## 7. Data Model & API
Models, entities, DTOs, endpoints, sample payloads, auth, error handling.

## 8. Milestone Progress
Approved, done, in progress, not started.

## 9. Decisions & Assumptions
Every decision taken without asking and every conservative default chosen. Anything
needing a human decision, repeated in your reply too.

## 10. Known Gaps / TODO
Deferred work, stubs and fakes, tech debt.

## 11. How to Build & Run
Commands, variants, env config, required keys and where they go.
```

---

## 8. Git and build environment

1. Check for a repository. If git is unavailable, note that and continue without it.
2. If there is no repository, run `git init`, add a proper Android `.gitignore` (`/build`,
   `.gradle`, `local.properties`, `.idea` except shared files, `*.apk`, `*.aab`,
   `*.keystore`, `*.jks`, captures), and commit the untouched starting state first.
3. Work on a feature branch unless told otherwise. Never force-push, never rewrite pushed
   history, and never commit secrets, keystores or `local.properties`.
4. Commit after each meaningful, self-consistent unit of work: a completed layer, a wired
   screen that builds, shared infrastructure (theme, navigation, DI), a bug fix, or a
   guide update. Don't commit only once at the end, or on every file save.
5. Every commit must compile where the environment can build. Run the build, and the tests
   if present, before committing. If this environment can't build (no SDK, wrong JDK,
   missing secrets), say so once at the start. Then run the strongest verification you
   can, and mark commits and reports as statically verified only. Never claim a build you
   didn't run.
6. Write conventional commit messages in the imperative, for example:
   - `feat(downloads): add download list screen and use cases`
   - `fix(player): release player when screen leaves composition`
   - `chore(di): register settings module`
   - `docs: update project guide for milestone 2`

   The body says what changed and why. Don't bundle unrelated changes.
7. Update the guide's checkboxes in the same commit as the work they describe.

---

## 9. Milestone framework

If no milestones were given, propose them during intake in this shape, adapted to the app.
Put risky, load-bearing work first: a beautiful screen on top of an engine that doesn't work
is wasted effort.

1. **Foundation.** Whatever the project is missing:
   - version catalog, `Application` class, Koin setup
   - persistence and network config
   - theme tokens generated from the design snapshot
   - the shared component library (§6 "Design → components")
   - the Navigation 3 shell

   The first changes of this milestone are `design/tokens.md` and `PROJECT-GUIDE.md`.
2. **Core data layer.** Models, entities, DTOs, DAOs, services, repositories and use cases,
   with unit tests for the logic that is genuinely non-trivial.
3. **The hardest subsystem.** Whatever the app fundamentally is and can't be reasoned about
   without running it (e.g. a download engine, a media player, background work). End it
   with a verification gate.
4. **Primary user flow**, end to end, one vertical slice at a time in the order a user meets
   them: domain → data → DI → ViewModel → components → screen → navigation → states →
   tests.
5. **Secondary flows**, with the same discipline.
6. **Integration.** Full wiring, back stack and deep links, process death and state
   restoration, permission flows, offline and error handling, transitions.
7. **Splash, onboarding, settings, monetisation**, and anything else that depends on the
   rest existing.
8. **QA.** Every edge case and every state, each marked pass, fail or not testable, plus
   accessibility: content descriptions, touch targets, font scaling and contrast.

Add a **gate** after any milestone whose correctness can only be observed, not reasoned
about, such as:

- background and foreground-service work, WorkManager or scheduled work
- media playback
- file saving across API levels
- permissions
- hardware
- orientation switches

At a gate, state plainly what must be verified on a real device and what you couldn't verify
yourself. Then apply the gate rule in §2.

Anything out of scope stays out. If a decision now would block a known future phase, mention
it, but don't pre-build it.

---

## 10. Definition of done

A milestone is not done until all of this is true.

**Build** *(run whatever this environment supports; see §8 item 5 if it can't build)*
- The project assembles.
- Unit tests pass, with focused tests for new use case, ViewModel and mapper logic.
- Lint is clean, or every suppression is justified.
- Nothing leaks across repeated cycles: no listeners, players, observers or wakelocks.

**Feature completeness**
- Every feature is wired end to end to its real data source. Where a backend or credential
  is genuinely unavailable, a clearly isolated fake sits behind the same repository
  interface, is listed in Known Gaps, and is never presented as production-complete. A
  hardcoded list in a ViewModel or composable is not acceptable.
- Every async surface handles loading, empty, error, retry and success.
- Every interactive element does what the design says it does.
- **Build it properly the first time.** Leave no placeholder screens and no unresolved
  `TODO` in anything you call complete. Record work you deliberately deferred in Known Gaps
  with the reason.
- **Never quietly simplify.** If you believe a simpler approach is better, say so and why
  *before* doing it.

**Verification matrix.** At the end of each milestone, mark every row verified, not
verified or not applicable. Never leave a row silently unanswered, and never mark one
verified on reasoning alone.

    [ ] Assembles                  [ ] Unit tests           [ ] Lint
    [ ] App launches               [ ] Back navigation      [ ] Process death / restore
    [ ] Rotation or window resize  [ ] Small screen         [ ] Large window or tablet
    [ ] Each theme the design has  [ ] Empty state          [ ] Error and retry
    [ ] Offline or slow network    [ ] Permission granted   [ ] Permission denied
    [ ] minSdk compatibility       [ ] Background behaviour [ ] Hardware the milestone uses

Report it as two short lists, verified and not verified. For anything you actually ran,
name the device or emulator and its API level.

**Conventions self-check.** Report the result of each of these honestly:
- Suffixes are correct (`Model`/`Dto`/`Entity`/`UiModel`/`UiState`/`Event`/`Effect`/
  `UseCase`/`RepositoryImpl`/`ViewModel`/`Screen`), none stacked, and nav keys are
  `Route.X`. ViewModel state flows are named after their `UiState` (`_mainUiState` /
  `mainUiState`), never a generic `_uiState`/`_state`.
- No DTO or entity reaches presentation, and no `UiModel` reaches data. Domain has no
  Android imports.
- One composable per file (previews excepted), and every file is named after its
  composable.
- The Screen file holds only the Screen composable. Components are stateless, take
  `modifier`, receive no ViewModel and have a preview.
- No component is duplicated where a parameter would do. Components shared across features
  live in `core/ui/`.
- No raw colour literal outside the theme, and no duplicate hex tokens.
- No `FontWeight.*` at a call site, and no hard-coded strings or screen-sized dimensions.
- Presentation touches no DAO, service, file or repository implementation.
- Each screen owns its background and insets, and no inset is applied twice.
- No deprecated API. Version-specific APIs are guarded or use compat.
- No unexpected permission in the merged manifest.
- No secret in source or commits, and nothing sensitive logged.
- No dependency or toolchain change beyond what the milestone required.

---

## 11. Reporting and honesty

At the end of every milestone, report:

- what was built, and why where the choice wasn't obvious
- which files changed
- the verification matrix: what you actually ran, on what device or emulator and API level,
  and what remains unverified and why
- assumptions and decisions taken without asking
- anything skipped, simplified, mocked or left incomplete
- the next milestone

Never claim a device or emulator test that didn't happen. Never tick a checkbox for stubbed
work, and never report "done" for something that doesn't build. An accurate "this part isn't
finished" is worth far more than a confident "complete" that costs an hour to disprove.

---

## 12. Parallel execution *(only if your tooling supports subagents)*

Parallelise work that touches **disjoint files**, serialise anything touching shared files,
and never parallelise git.

- **Analysis:** one agent maps the repo structure, one traces existing slices, and one
  inventories design screens and repeating components. You synthesise the findings.
- **Foundation:** parallelise theme tokens and independent shared components. Serialise
  navigation and DI registration.
- **Feature slices:** one agent per independent feature, each building a full vertical
  slice. Build shared or blocking features first, sequentially.
- **Within one feature:** split domain+data from UI only after the contract is fixed
  (models, use case signatures, UiState/Event shape).
- **Verification:** one agent per screen compares it against its design frame, plus passes
  for architecture conformance and accessibility.

Hard constraints:
- **You own git:** subagents never commit, branch or stage.
- **You own shared files:** DI modules, navigation, the version catalog, theme tokens,
  `core/ui/`, string resources and the project guide. Agents report what they need and you
  apply it.
- Give every agent these conventions, its exact allowed file paths and its design frames.
- Build after integrating each agent's work and verify it yourself.
- Cap the fan-out at about three to five concurrent agents.

---

## 13. Start here

0. Read this entire document, including the Project Inputs. If you were handed a resume
   prompt, read `PROJECT-GUIDE.md` and continue from its milestone progress instead of
   repeating intake.
1. Read the design and the repository.
2. Classify the project state (§3.2) and the UI toolkit (§5.0).
3. Report, without writing any file:
   - your understanding of the app in a few lines
   - the project-state classification and the toolkit
   - the execution mode
   - gaps or conflicts between the inputs and the design
   - the design tokens you extracted
   - the shared component inventory
   - the project guide content you intend to write
   - anything you think is technically risky
   - your batched questions, each with a recommended default
   - your proposed milestone plan
4. Wait for approval. Then build milestone one to completion and report, continuing or
   stopping according to the execution mode.

**The bar for every build:** a shipping-quality app that matches the design exactly,
implements every requested feature completely, obeys these conventions literally, and is
verified rather than assumed.
