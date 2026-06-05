# User Management Editing — Design Spec

- **Document ID:** internal design — identity module, user administration
- **Date:** 2026-06-06
- **Status:** Approved (design)
- **Module / process:** `identity` — edit existing users (name, roles, active status, password)

## 1. Context & problem
The `identity` module ships only three use-cases: `login`, `createuser`, `listusers` (+ get-by-id).
There is **no update endpoint anywhere** and the Users admin page is a read-only table plus a
"New User" dialog. Consequently an administrator **cannot**, after a user is created:
- change their **role assignments**,
- **activate / deactivate** the account (e.g. disable a departed employee),
- **reset the password** (a forgotten password currently means creating a brand-new user),
- correct the **display name**.

This is the production gap the client reported as "for users/roles/permissions none are editable".
Roles in this system *are* the permission model — they drive both nav visibility and every
`@PreAuthorize` check — and they are a fixed code enum (`Role`). So "make roles/permissions
editable" means **editing which roles a user holds**, not building a dynamic RBAC engine (explicit
non-goal, see §7).

## 2. Goals
| Ref | Requirement |
|---|---|
| E1 | `PUT /api/users/{id}` (ADMIN) updates `fullName`, `roles`, and `active` in one call. |
| E2 | `POST /api/users/{id}/password` (ADMIN) sets a new password (re-hashed); returns 204. |
| E3 | Username is **immutable** (it is the login key); not accepted by either endpoint. |
| E4 | Editing is **ADMIN-only**; any other role → 403 (mirrors create/list). |
| E5 | Unknown id → 404 `NOT_FOUND`; concurrent edit → 409 `CONCURRENT_MODIFICATION` (existing `@Version`). |
| G1 | **Last-admin guard:** a change that would leave **zero active admins** is refused → 409 `LAST_ADMIN`. |
| G2 | **Self-deactivate guard:** an admin cannot set their *own* account inactive → 422 `CANNOT_DEACTIVATE_SELF`. |
| G3 | **Self-demote guard:** an admin cannot remove ADMIN from their *own* roles → 422 `CANNOT_DEMOTE_SELF`. |
| UI | Users page gains a per-row **Edit** action → dialog (name + role chips + active toggle + reset-password); toasts; en/ar i18n. |

## 3. Non-goals
- A dynamic roles/permissions system (custom roles, granular permission rows, per-permission
  assignment). Roles stay the fixed `Role` enum.
- Editing the username, deleting users (deactivate is the soft-delete), bulk operations.
- Self-service "forgot password" (this is admin-initiated reset only).
- Any DB migration — every column already exists (`app_user`, `user_role`).

## 4. Architecture & changes

### 4.1 Domain — `identity/domain/User.java`
Add three intention-revealing mutators next to the existing `deactivate()` / `changePassword()`:
- `rename(String fullName)` — sets `fullName` (blank → `DomainException("USER_FULLNAME_REQUIRED", …)`).
- `replaceRoles(Set<Role> roles)` — null/empty → `DomainException("USER_ROLES_REQUIRED", …)` (reuses the
  create-path invariant); otherwise `this.roles = new HashSet<>(roles)`.
- `activate()` — sets `active = true`.

No state machine; these are simple guarded setters. The aggregate stays the single source of truth
for its invariants (a user always has ≥1 role).

### 4.2 Slice `updateuser` (mirrors `createuser`)
- `UpdateUserCommand(@NotBlank @Size(max=200) String fullName, @NotEmpty Set<Role> roles, boolean active)`.
- `UpdateUserController`: `@PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")`, injects
  `@AuthenticationPrincipal HmsUserPrincipal` to pass the acting user id; returns `UserResponse`.
- `UpdateUserHandler.handle(UUID id, UpdateUserCommand cmd, UUID actingUserId)` `@Transactional`:
  1. `User u = users.findById(id).orElseThrow(NotFoundException…)`.
  2. **Guards** (evaluated before mutation):
     - `id.equals(actingUserId) && !cmd.active()` → `DomainException("CANNOT_DEACTIVATE_SELF", …)`.
     - `id.equals(actingUserId) && !cmd.roles().contains(ADMIN)` → `DomainException("CANNOT_DEMOTE_SELF", …)`.
     - `wasActiveAdmin && !willBeActiveAdmin && users.countActiveAdmins() <= 1` →
       `ConflictException("LAST_ADMIN", …)`, where `wasActiveAdmin = u.isActive() && u.getRoles().contains(ADMIN)`
       and `willBeActiveAdmin = cmd.active() && cmd.roles().contains(ADMIN)`.
  3. Apply: `u.rename(fullName)`, `u.replaceRoles(roles)`, `cmd.active() ? u.activate() : u.deactivate()`.
  4. `return users.save(u)`.

### 4.3 Slice `resetpassword`
- `ResetPasswordCommand(@NotBlank @Size(min=6, max=100) String newPassword)`.
- `ResetPasswordController`: `@PostMapping("/{id}/password") @PreAuthorize("hasRole('ADMIN')")`,
  returns `ResponseEntity<Void>` 204.
- `ResetPasswordHandler.handle(UUID id, ResetPasswordCommand cmd)` `@Transactional`: load (404) →
  `u.changePassword(encoder.encode(cmd.newPassword()))` → save. (No self/last-admin guard: resetting
  a password neither removes admins nor locks anyone out.)

### 4.4 Repository — `UserRepository`
Add an admin counter (derived queries over an `@ElementCollection` are unreliable, so use JPQL):
```java
@Query("select count(u) from User u join u.roles r where r = com.albudoor.hms.identity.domain.Role.ADMIN and u.active = true")
long countActiveAdmins();
```

### 4.5 Error contract
All codes flow through the existing `GlobalExceptionHandler`: `NotFoundException`→404,
`ConflictException`→409, `DomainException`→422, `AccessDeniedException`→403,
`MethodArgumentNotValidException`→400, `OptimisticLockingFailureException`→409. No handler changes.

## 5. Frontend (`features/admin/users`)
- `api.ts`: `updateUser(id, { fullName, roles, active }): Promise<AppUser>` (`PUT /users/{id}`) and
  `resetUserPassword(id, newPassword): Promise<void>` (`POST /users/{id}/password`).
- `UsersPage.tsx`: add a trailing **Actions** column with an **Edit** button per row →
  `EditUserDialog` (extracted, reusing the role-chip selector + `Input` from the create dialog):
  - name field (pre-filled), role chips (pre-selected from `user.roles`), an **Active / Disabled**
    toggle, and a **Reset password** field with its own Save (calls `resetUserPassword`).
  - Save → `updateUser` mutation → `invalidateQueries(['users'])` + success toast; guard errors
    (`LAST_ADMIN`, `CANNOT_DEACTIVATE_SELF`, `CANNOT_DEMOTE_SELF`) surfaced via `extractApiError`.
- i18n: add `users.edit*`, `users.active`, `users.disabled`, `users.statusLabel`,
  `users.resetPassword`, `users.passwordReset`, `users.updated`, `users.updateFailed`,
  and friendly messages for the three guard codes — both `en` and `ar`, RTL-safe.

## 6. Testing (production-grade)
- **Domain unit (`UserTest`):** `rename` happy + blank→throws; `replaceRoles` happy + empty→throws;
  `activate`/`deactivate` flip `active`; `changePassword` blank→throws.
- **Integration (Failsafe, `UserAdminIT extends IntegrationTest`):**
  - create a fresh `RECEPTIONIST` via API, then `PUT` → name/roles/active change persisted (re-`GET`);
  - `deactivate` that fresh user → `active=false`; reactivate → `active=true`;
  - reset its password → login with the new password succeeds, old password → 401;
  - **guard:** `PUT` deactivating sole admin (`admin`) → 409 `LAST_ADMIN`; removing ADMIN from `admin`
    → 409 `LAST_ADMIN`; `admin` deactivating self → 422 `CANNOT_DEACTIVATE_SELF`;
  - **authz:** `receptionist` token → 403 on `PUT` and on reset-password;
  - unknown id → 404.
- **E2E (Playwright, `user-admin.spec.ts`):** admin opens Users → Edit a user → change roles + toggle
  Disabled → Save → row shows new roles + Disabled badge; reset password → log out → that user logs
  in with the new password.
- Gate: `mvn -pl identity,app -am verify` green; `tsc -b` + `npm run build` clean; new e2e green.

## 7. Open assumptions
- "Permissions" = roles (no separate permission entity); editing = changing a user's `Role` set.
- Soft-delete only (deactivate); no hard user deletion in this pass.
- Sole seeded admin is `admin`; the last-admin guard is what makes deactivating/De-adminning it safe.
