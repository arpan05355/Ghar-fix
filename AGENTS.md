# GharFix Development Rules

## General

- Inspect existing code before creating new files.
- Reuse existing services, utilities, components, and API clients.
- Do not duplicate business logic.
- Make the smallest change necessary to satisfy the request.
- Do not modify unrelated functionality.
- Preserve existing API contracts unless a change is explicitly requested.
- Do not delete existing functionality unless explicitly instructed.

## Security

- Never hard-code API keys, passwords, tokens, or secrets.
- Never expose GEMINI_API_KEY to frontend code.
- Keep Gemini API calls on the backend.
- Validate all AI-generated inputs on the backend.
- Never trust AI output for authorization, payment, refund, booking ownership, or security decisions.
- Do not log sensitive credentials or personal data unnecessarily.

## Backend

- Follow the existing Spring Boot architecture.
- Reuse existing services and repositories.
- Keep business logic in backend services rather than controllers.
- Preserve existing authentication and authorization behavior.
- Run Maven tests after backend changes.

## Frontend

- Reuse existing GharFix components and styling.
- Follow the existing design system and theme.
- Do not introduce unnecessary frameworks or dependencies.
- Keep frontend business logic minimal.
- Never put secrets or Gemini credentials in frontend code.
- Run the frontend build/tests after frontend changes.

## AI

- Treat Gemini output as untrusted input.
- Do not allow Gemini to directly execute business-critical actions.
- Backend must validate AI-generated actions and parameters.
- Require explicit user confirmation for destructive or financial actions.
- Do not allow AI-generated prices to override authoritative backend/database pricing.

## Testing

- Run relevant tests after every significant change.
- Fix regressions rather than ignoring them.
- Report files changed and tests/build results after implementation.
