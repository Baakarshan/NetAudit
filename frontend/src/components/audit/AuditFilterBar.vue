<template>
  <el-card class="filter-card">
    <el-form :inline="true" :model="filters">
      <el-form-item label="协议">
        <el-select v-model="filters.protocol" placeholder="全部">
          <el-option label="全部" value="" />
          <el-option v-for="p in protocols" :key="p" :label="p" :value="p" />
        </el-select>
      </el-form-item>
      <el-form-item label="源IP">
        <el-input v-model="filters.srcIp" placeholder="192.168.1.1" />
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="filters.range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">搜索</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
const protocols = ['HTTP', 'FTP', 'TELNET', 'DNS', 'SMTP', 'POP3']
const filters = defineModel<{
  protocol: string
  srcIp: string
  range: [Date, Date] | null
}>({
  default: { protocol: '', srcIp: '', range: null }
})

const emit = defineEmits<{ (e: 'search'): void }>()

const onSearch = () => emit('search')
</script>

<style scoped>
.filter-card {
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  margin-bottom: 16px;
}
</style>
