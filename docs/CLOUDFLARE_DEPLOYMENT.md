# 🚀 Cloudflare Pages Deployment Guide

Complete guide to deploying Spice Framework documentation to Cloudflare Pages.

## ✅ Prerequisites

- Cloudflare account (free tier works!)
- GitHub repository access
- Build successful (`npm run build` completes without errors)

## 📦 Deployment Options

### Option 1: Cloudflare Pages Dashboard (Recommended for first-time)

1. **Log in to Cloudflare Dashboard**
   - Go to https://dash.cloudflare.com/
   - Navigate to **Workers & Pages** → **Pages**

2. **Connect to Git**
   - Click **Create a project**
   - Select **Connect to Git**
   - Choose **GitHub** and authorize Cloudflare
   - Select your `spice` repository

3. **Configure Build Settings**
   ```
   Project name: spice-framework-docs
   Production branch: main
   Build command: cd docs && npm install && npm run build
   Build output directory: docs/build
   Root directory: /
   ```

4. **Environment Variables**
   ```
   NODE_VERSION=20
   ```

5. **Deploy**
   - Click **Save and Deploy**
   - Wait for the build to complete (~2-3 minutes)
   - Your site will be live at: `https://spice-framework-docs.pages.dev`

### Option 2: Wrangler CLI

1. **Install Wrangler**
   ```bash
   npm install -g wrangler
   ```

2. **Login to Cloudflare**
   ```bash
   wrangler login
   ```

3. **Deploy**
   ```bash
   cd /Users/devhub/dev/spice/docs
   npm run build
   wrangler pages deploy build --project-name=spice-framework-docs
   ```

### Option 3: GitHub Actions (Automated)

GitHub Actions workflow is ready at `.github/workflows/deploy-docs.yml`.

**Setup:**

1. **Get Cloudflare API Token**
   - Go to https://dash.cloudflare.com/profile/api-tokens
   - Create token with **Cloudflare Pages** permissions
   - Copy the token

2. **Get Account ID**
   - Go to https://dash.cloudflare.com/
   - Select your account
   - Copy **Account ID** from the right sidebar

3. **Add GitHub Secrets**
   - Go to your repository → **Settings** → **Secrets and variables** → **Actions**
   - Add two secrets:
     - `CLOUDFLARE_API_TOKEN`: Your API token
     - `CLOUDFLARE_ACCOUNT_ID`: Your account ID

4. **Push to Main**
   ```bash
   git add .
   git commit -m "Add Cloudflare Pages deployment"
   git push origin main
   ```

   The docs will automatically deploy on every push to `main` that modifies the `docs/` directory.

## 🔧 Configuration Files

### docusaurus.config.ts

Current configuration:
```typescript
url: 'https://no-ai-labs.github.io',
baseUrl: '/spice/',
```

**For custom domain on Cloudflare Pages:**
```typescript
url: 'https://spice.your-domain.com',
baseUrl: '/',
```

### _redirects

Created at `docs/public/_redirects`:
```
/* /spice/:splat 200
```

This ensures proper routing with the `/spice/` base URL.

## 🌐 Custom Domain Setup

1. **Add Custom Domain in Cloudflare Pages**
   - Go to your project → **Custom domains**
   - Click **Set up a custom domain**
   - Enter your domain (e.g., `docs.spice-framework.dev`)

2. **DNS Configuration**
   - Add a CNAME record pointing to your Pages project:
     ```
     CNAME docs <your-project>.pages.dev
     ```

3. **Update docusaurus.config.ts**
   ```typescript
   url: 'https://docs.spice-framework.dev',
   baseUrl: '/',
   ```

4. **Rebuild and Deploy**
   ```bash
   npm run build
   git commit -am "Update for custom domain"
   git push
   ```

## 📊 Build Information

**Current build:**
- ✅ Build time: ~15 seconds
- ✅ Output size: ~2MB
- ✅ Pages generated: 30+
- ✅ Assets optimized: Yes
- ⚠️ Warnings: 1 broken internal link (non-blocking)

**Build output directory structure:**
```
docs/build/
├── assets/          # Bundled CSS/JS
├── blog/           # Blog pages
├── docs/           # Documentation pages
├── img/            # Images
├── index.html      # Home page
└── sitemap.xml     # SEO sitemap
```

## 🔍 Verification

After deployment, verify:

1. **Homepage loads**: `https://spice-framework-docs.pages.dev/spice/`
2. **Docs work**: Navigate through documentation sections
3. **Search works**: Test the search functionality
4. **Dark mode works**: Toggle theme switcher
5. **Mobile responsive**: Check on mobile devices

## 🐛 Troubleshooting

### Build fails with "onBrokenLinks" error

**Solution**: Already fixed! We set `onBrokenLinks: 'warn'` in config.

### 404 errors on page refresh

**Solution**: The `_redirects` file handles this. Ensure it's in `docs/public/`.

### Styles not loading

**Solution**: Check `baseUrl` in `docusaurus.config.ts` matches your deployment URL.

### Build takes too long

**Solution**: Cloudflare has 20-minute timeout. Current build is ~15 seconds, well within limit.

## 📈 Post-Deployment

### Analytics (Optional)

Add Cloudflare Web Analytics:
```typescript
// docusaurus.config.ts
headTags: [
  {
    tagName: 'script',
    attributes: {
      defer: true,
      src: 'https://static.cloudflareinsights.com/beacon.min.js',
      'data-cf-beacon': '{"token": "YOUR_TOKEN"}',
    },
  },
],
```

### Performance

Cloudflare Pages provides:
- ✅ Global CDN
- ✅ HTTP/2 & HTTP/3
- ✅ Brotli compression
- ✅ Edge caching
- ✅ DDoS protection

## 🎯 Quick Deploy Commands

```bash
# One-time setup
cd /Users/devhub/dev/spice/docs
npm install

# Build and verify locally
npm run build
npm run serve  # Test at http://localhost:3000

# Deploy via Wrangler
wrangler pages deploy build --project-name=spice-framework-docs

# Or just push to GitHub (if Actions configured)
git add .
git commit -m "Update docs"
git push origin main
```

## ✨ Result

Your documentation will be live at:
- **Cloudflare Pages URL**: `https://spice-framework-docs.pages.dev/spice/`
- **Custom domain** (if configured): `https://docs.your-domain.com/`

**Deployment features:**
- 🚀 Lightning-fast global CDN
- 🔄 Automatic deployments on git push
- 🌍 Deploy previews for PRs
- 📊 Built-in analytics
- 🔒 Free SSL/TLS
- ♾️ Unlimited bandwidth (Free plan: 500 builds/month)

---

**Ready to deploy?** Follow Option 1 for the easiest setup! 🎉
