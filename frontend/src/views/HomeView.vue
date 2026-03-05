<template>
  <div class="home">
    <el-container>
      <el-header>
        <h1>NetAudit 网络审计系统</h1>
      </el-header>
      <el-main>
        <el-card>
          <h2>欢迎使用 NetAudit</h2>
          <p>实时网络协议捕获与分析系统</p>
          <el-space>
            <el-button type="primary" @click="$router.push('/capture')">
              开始捕获
            </el-button>
            <el-button @click="$router.push('/analysis')">
              数据分析
            </el-button>
          </el-space>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
// 首页视图
</script>

<style scoped>
.home {
  width: 100%;
  height: 100%;
}

.el-header {
  display: flex;
  align-items: center;
  background-color: #409eff;
  color: white;
}

.el-main {
  display: flex;
  justify-content: center;
  align-items: center;
}

.el-card {
  text-align: center;
  min-width: 400px;
}

h2 {
  margin-bottom: 20px;
}

p {
  margin-bottom: 30px;
  color: #666;
}
</style>
