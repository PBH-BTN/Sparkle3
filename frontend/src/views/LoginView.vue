<script setup lang="ts">
import { IconFire, IconGithub, IconLock } from '@arco-design/web-vue/es/icon'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'

const { t } = useI18n()
</script>

<template>
  <main class="login-page">
    <div class="login-language">
      <LanguageSwitcher />
    </div>
    <section class="login-panel">
      <div class="login-brand">
        <span class="login-logo"><IconFire /></span>
        <span>{{ t('app.title') }}</span>
      </div>

      <div class="login-heading">
        <a-tag color="arcoblue" size="small">{{ t('auth.badge') }}</a-tag>
        <h1>{{ t('auth.title') }}</h1>
        <h2>{{ t('auth.subtitle') }}</h2>
        <p>{{ t('auth.description') }}</p>
      </div>

      <a
        class="github-login"
        href="/oauth2/authorization/github"
        aria-label="GitHub OAuth"
      >
        <IconGithub />
        {{ t('auth.signIn') }}
      </a>

      <p class="security-note">
        <IconLock />
        {{ t('auth.securityNote') }}
      </p>
    </section>
    <footer>{{ t('app.footer') }}</footer>
  </main>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 20px;
  overflow: hidden;
  background:
    radial-gradient(circle at 10% 10%, rgb(var(--primary-1)) 0, transparent 32%),
    radial-gradient(circle at 90% 80%, rgb(var(--primary-1)) 0, transparent 30%),
    var(--color-fill-2);
}

.login-language {
  position: absolute;
  top: 20px;
  right: 24px;
}

.login-panel {
  width: min(460px, 100%);
  padding: 40px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  box-shadow: 0 12px 40px rgb(0 0 0 / 8%);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 44px;
  color: var(--color-text-2);
  font-size: 14px;
  font-weight: 600;
}

.login-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: white;
  font-size: 20px;
  background: rgb(var(--primary-6));
  border-radius: 10px;
}

.login-heading h1 {
  margin: 18px 0 4px;
  color: var(--color-text-1);
  font-size: 30px;
  font-weight: 650;
  line-height: 1.3;
}

.login-heading h2 {
  margin: 0 0 16px;
  color: rgb(var(--primary-6));
  font-size: 16px;
  font-weight: 500;
}

.login-heading p {
  margin: 0 0 28px;
  color: var(--color-text-3);
  line-height: 1.7;
}

.github-login {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 44px;
  color: var(--color-white);
  font-weight: 600;
  text-decoration: none;
  background: var(--color-text-1);
  border-radius: 6px;
  transition:
    transform 0.15s,
    opacity 0.15s;
}

.github-login:hover {
  color: var(--color-white);
  opacity: 0.9;
  transform: translateY(-1px);
}

.security-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 20px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.security-note svg {
  flex: 0 0 auto;
  margin-top: 2px;
}

footer {
  margin-top: 28px;
  color: var(--color-text-3);
  font-size: 12px;
}

@media (max-width: 520px) {
  .login-panel {
    padding: 28px 22px;
  }

  .login-heading h1 {
    font-size: 25px;
  }
}
</style>
