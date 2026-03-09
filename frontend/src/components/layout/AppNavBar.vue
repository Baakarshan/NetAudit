<template>
  <header class="nav-bar">
    <div class="brand">
      <div class="brand-mark">N</div>
      <div>
        <div class="brand-title">NetAudit</div>
        <div class="brand-sub">网络审计与实时告警</div>
      </div>
    </div>

    <nav class="nav-links">
      <RouterLink to="/dashboard" class="nav-link" active-class="active">仪表盘</RouterLink>
      <RouterLink to="/audit" class="nav-link" active-class="active">审计日志</RouterLink>
      <RouterLink to="/alerts" class="nav-link" active-class="active">告警中心</RouterLink>
    </nav>

    <div class="nav-status">
      <el-tag type="success" effect="dark">在线</el-tag>
      <el-tag type="info" effect="plain">SSE</el-tag>
    </div>
  </header>
</template>

<script setup lang="ts">
</script>

<style scoped>
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 18px;
  box-shadow: 0 8px 20px rgba(14, 165, 233, 0.3);
}

.brand-title {
  font-weight: 700;
  font-size: 18px;
}

.brand-sub {
  font-size: 12px;
  color: var(--text-2);
}

.nav-links {
  display: flex;
  gap: 16px;
  align-items: center;
}

.nav-link {
  text-decoration: none;
  color: var(--text-2);
  padding: 8px 14px;
  border-radius: 999px;
  transition: all 0.2s ease;
  background: transparent;
}

.nav-link:hover {
  color: var(--text-1);
  background: rgba(14, 165, 233, 0.12);
}

.nav-link.active {
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: 0 6px 14px rgba(14, 165, 233, 0.25);
}

.nav-status {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
