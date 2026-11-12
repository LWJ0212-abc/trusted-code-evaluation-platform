<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Activity, ArrowRight, BookOpen, CheckCircle2, ChevronRight, LayoutDashboard, LogOut, RefreshCw, ShieldCheck } from 'lucide-vue-next'
import { getCurrentUser, logout, type ApiFailure, type CurrentUser } from './api/auth'

type ViewState = 'loading' | 'signed-out' | 'authenticated' | 'error' | 'logging-out'

const state = ref<ViewState>('loading')
const user = ref<CurrentUser | null>(null)
const failure = ref<ApiFailure | null>(null)
const authError = computed(() => new URLSearchParams(window.location.search).has('auth_error'))

const stateTitle = computed(() => {
  if (state.value === 'loading') return '正在确认登录状态'
  if (state.value === 'error') return '暂时无法连接平台'
  if (state.value === 'authenticated') return '身份已确认'
  return '欢迎使用可信代码评测平台'
})

async function loadUser() {
  state.value = 'loading'
  failure.value = null
  try {
    user.value = await getCurrentUser()
    state.value = 'authenticated'
  } catch (error) {
    const problem = error as ApiFailure
    user.value = null
    failure.value = problem
    state.value = problem.status === 401 ? 'signed-out' : 'error'
  }
}

async function signOut() {
  state.value = 'logging-out'
  try {
    await logout()
    user.value = null
    state.value = 'signed-out'
  } catch (error) {
    failure.value = error as ApiFailure
    state.value = 'error'
  }
}

function login() {
  window.location.assign('/api/v1/auth/login')
}

onMounted(loadUser)
</script>

<template>
  <main class="app-shell" :class="{ 'app-shell--public': state === 'signed-out' || state === 'error' || state === 'loading' }">
    <aside class="sidebar" aria-label="主导航">
      <a class="brand" href="/" aria-label="可信代码评测平台首页">
        <span class="brand-mark"><ShieldCheck :size="19" :stroke-width="2.2" /></span>
        <span>可信代码评测平台</span>
      </a>
      <div class="sidebar-section-label">工作区</div>
      <nav class="sidebar-nav">
        <a class="sidebar-link sidebar-link--active" href="/">
          <LayoutDashboard :size="17" aria-hidden="true" />
          <span>身份总览</span>
        </a>
        <span class="sidebar-link sidebar-link--muted" aria-disabled="true">
          <BookOpen :size="17" aria-hidden="true" />
          <span>课程与作业</span>
          <small>即将开放</small>
        </span>
        <span class="sidebar-link sidebar-link--muted" aria-disabled="true">
          <Activity :size="17" aria-hidden="true" />
          <span>评测活动</span>
          <small>即将开放</small>
        </span>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-status"><span class="status-dot"></span><span>平台服务正常</span></div>
        <div class="sidebar-environment">M0 · 本地开发环境</div>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumb"><span>工作区</span><ChevronRight :size="14" aria-hidden="true" /><strong>身份总览</strong></div>
        <span class="environment-label">M0 身份环境</span>
      </header>

      <section class="content-frame" aria-live="polite">
      <template v-if="state === 'loading'">
        <div class="loading-state">
          <span class="spinner" aria-hidden="true"></span>
          <p>正在确认你的平台会话…</p>
        </div>
      </template>

      <template v-else-if="state === 'signed-out'">
        <div class="welcome-layout">
          <div class="welcome-copy">
            <p class="eyebrow">可信代码评测平台 · M0</p>
            <h1>{{ stateTitle }}</h1>
            <p class="lede">统一身份认证已接入。登录后查看你的平台权限与课程访问范围。</p>
            <p v-if="authError" class="notice notice--warning" role="alert">身份认证未完成，请重试。</p>
            <a class="primary-button" href="/api/v1/auth/login" @click.prevent="login">
              校园身份登录
              <ArrowRight :size="18" aria-hidden="true" />
            </a>
            <div class="trust-row"><CheckCircle2 :size="16" aria-hidden="true" /><span>服务端会话 · 安全连接 · 内部身份映射</span></div>
          </div>
          <div class="welcome-aside">
            <div class="aside-panel">
              <div class="aside-panel-heading"><span class="status-dot"></span><span>身份服务在线</span></div>
              <div class="aside-stat">OIDC <strong>READY</strong></div>
              <p>你的学校身份将在服务端完成验证，页面不会接触访问令牌。</p>
            </div>
          </div>
        </div>
      </template>

      <template v-else-if="state === 'error'">
        <div class="error-state" role="alert">
          <p class="eyebrow">SERVICE STATUS</p>
          <h1>{{ stateTitle }}</h1>
          <p>{{ failure?.message || '服务端暂时不可用，请稍后再试。' }}</p>
          <p v-if="failure?.trace_id" class="trace-line">追踪编号 <code>{{ failure.trace_id }}</code></p>
          <button class="secondary-button" type="button" @click="loadUser">
            <RefreshCw :size="17" aria-hidden="true" /> 重试
          </button>
        </div>
      </template>

      <template v-else>
        <div class="dashboard-heading">
          <div>
            <p class="eyebrow">平台身份 / OVERVIEW</p>
            <h1>{{ stateTitle }}</h1>
            <p class="lede">这是你在平台中的内部身份与课程访问范围。</p>
          </div>
          <div class="account-actions">
            <div class="account-chip"><span class="avatar">{{ user?.user_id.slice(-2) }}</span><span>已认证用户</span></div>
            <button class="quiet-button" type="button" :disabled="state === 'logging-out'" @click="signOut">
              <LogOut :size="17" aria-hidden="true" />
              {{ state === 'logging-out' ? '正在退出' : '退出登录' }}
            </button>
          </div>
        </div>

        <div v-if="user" class="identity-grid">
          <section class="identity-card identity-card--wide">
            <div class="card-heading"><p class="card-label">平台用户 ID</p><span class="card-index">01</span></div>
            <code class="user-id">{{ user.user_id }}</code>
            <p class="card-footnote">稳定的平台内部标识</p>
          </section>
          <section class="identity-card">
            <div class="card-heading"><p class="card-label">平台角色</p><span class="card-index">02</span></div>
            <div v-if="user.platform_roles.length" class="tag-list">
              <span v-for="role in user.platform_roles" :key="role" class="tag">{{ role }}</span>
            </div>
            <p v-else class="empty-copy">暂无平台角色</p>
          </section>
          <section class="identity-card identity-card--status">
            <div class="card-heading"><p class="card-label">会话状态</p><span class="card-index">03</span></div>
            <div class="session-state"><span class="status-dot"></span><strong>已连接</strong></div>
            <p class="card-footnote">平台 Session 正在保护当前访问</p>
          </section>
        </div>

        <section v-if="user" class="membership-section">
          <div class="section-heading">
            <div>
              <p class="card-label">课程成员关系</p>
              <h2>你可以访问的课程</h2>
            </div>
            <span class="count-label">{{ user.memberships.length }} 门课程</span>
          </div>
          <div v-if="user.memberships.length" class="membership-table" role="table" aria-label="课程成员关系">
            <div class="table-row table-row--header" role="row">
              <span role="columnheader">课程 ID</span><span role="columnheader">课程角色</span>
            </div>
            <div v-for="membership in user.memberships" :key="membership.course_id" class="table-row" role="row">
              <code role="cell">{{ membership.course_id }}</code><span role="cell" class="role-value">{{ membership.role }}</span>
            </div>
          </div>
          <p v-else class="empty-panel">当前没有课程成员关系。</p>
        </section>
      </template>
      </section>
      <footer class="footer"><span>身份边界 · M0</span><span v-if="user?.trace_id">Trace {{ user.trace_id }}</span></footer>
    </section>
  </main>
</template>
