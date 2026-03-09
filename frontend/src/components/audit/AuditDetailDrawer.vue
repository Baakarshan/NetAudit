<template>
  <el-drawer v-model="visible" size="40%" title="事件详情" :with-header="true">
    <div v-if="event" class="detail">
      <section class="section">
        <h4>基础信息</h4>
        <div class="grid">
          <div>协议：{{ event.protocol }}</div>
          <div>时间：{{ event.timestamp }}</div>
          <div>源IP：{{ event.srcIp }}:{{ event.srcPort }}</div>
          <div>目标IP：{{ event.dstIp }}:{{ event.dstPort }}</div>
          <div>告警级别：{{ event.alertLevel }}</div>
        </div>
      </section>

      <section class="section">
        <h4>协议字段</h4>
        <pre class="payload">{{ event }}</pre>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AuditEvent } from '@/types/models'

const props = defineProps<{
  modelValue: boolean
  event: AuditEvent | null
}>()

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})
</script>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section h4 {
  margin-bottom: 8px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  color: var(--text-2);
}

.payload {
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 10px;
  font-size: 12px;
  overflow: auto;
}
</style>
