# Gmail App Password Setup

Google no longer allows "Less Secure Apps" to use your regular Gmail password. You must use an **App Password** for SMTP authentication.

## Prerequisites

- A Google account
- 2-Step Verification enabled on your Google account

## Steps to Create a Gmail App Password

### 1. Enable 2-Step Verification

1. Go to [Google Account Security](https://myaccount.google.com/security)
2. Under "How you sign in to Google", click **2-Step Verification**
3. Follow the prompts to enable 2-Step Verification (if not already enabled)

### 2. Create an App Password

1. Go to [App Passwords](https://myaccount.google.com/apppasswords)
   - Or: Google Account → Security → 2-Step Verification → App passwords
2. You may need to sign in again
3. Under "Select app", choose **Mail**
4. Under "Select device", choose **Other (Custom name)** and enter e.g. "MCart Auth Service"
5. Click **Generate**
6. Google will display a **16-character password** (e.g. `abcd efgh ijkl mnop`)
7. **Copy this password** — you won't be able to see it again

### 3. Configure the Application

Set the App Password as your SMTP password. **Do not use your regular Gmail password.**

**Option A: Environment variables (recommended)**

```bash
export SPRING_MAIL_USERNAME=your-email@gmail.com
export SPRING_MAIL_PASSWORD=abcdefghijklmnop   # 16 chars, no spaces
```

**Option B: application.properties**

```properties
spring.mail.username=your-email@gmail.com
spring.mail.password=abcdefghijklmnop
spring.mail.test-connection=true   # optional: verify SMTP connection at startup
```

> **Security:** Never commit real credentials to version control. Use environment variables or a secrets manager in production.
> **Note:** Remove spaces from the 16-character App Password when pasting (e.g. `abcd efgh ijkl mnop` → `abcdefghijklmnop`).

### 4. Verification Link Base URL

For the verification email link to work, set the base URL of your auth service:

```bash
export AUTH_VERIFICATION_BASE_URL=http://localhost:8081
```

In production, use your actual domain, e.g. `https://auth.yourdomain.com`.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Username and Password not accepted" | Use App Password, not your regular Gmail password |
| "App Passwords" option not visible | Enable 2-Step Verification first |
| Emails not arriving | Check spam folder; verify `spring.mail.debug=true` for logs |
| Connection timeout | Ensure port 587 (STARTTLS) is not blocked by firewall |

## Gmail SMTP Settings (Reference)

- **Host:** smtp.gmail.com
- **Port:** 587 (TLS)
- **Auth:** Required
- **StartTLS:** Enabled
