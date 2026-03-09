export type ProtocolType = 'HTTP' | 'FTP' | 'TELNET' | 'DNS' | 'SMTP' | 'POP3'
export type AlertLevel = 'INFO' | 'WARN' | 'CRITICAL'

export interface BaseAuditEvent {
  id: string
  timestamp: string
  srcIp: string
  dstIp: string
  srcPort: number
  dstPort: number
  protocol: ProtocolType
  alertLevel: AlertLevel
}

export interface HttpEvent extends BaseAuditEvent {
  protocol: 'HTTP'
  method: string
  url: string
  host: string
  userAgent?: string
  contentType?: string
  statusCode?: number
}

export interface FtpEvent extends BaseAuditEvent {
  protocol: 'FTP'
  username?: string
  command: string
  argument?: string
  responseCode?: number
  currentDirectory?: string
}

export interface TelnetEvent extends BaseAuditEvent {
  protocol: 'TELNET'
  username?: string
  commandLine: string
  direction: string
}

export interface DnsEvent extends BaseAuditEvent {
  protocol: 'DNS'
  transactionId: number
  queryDomain: string
  queryType: string
  isResponse: boolean
  resolvedIps: string[]
  responseTtl?: number
}

export interface SmtpEvent extends BaseAuditEvent {
  protocol: 'SMTP'
  from?: string
  to: string[]
  subject?: string
  attachmentNames: string[]
  attachmentSizes: number[]
  stage?: string
}

export interface Pop3Event extends BaseAuditEvent {
  protocol: 'POP3'
  username?: string
  command: string
  from?: string
  to: string[]
  subject?: string
  attachmentNames: string[]
  attachmentSizes: number[]
  mailSize?: number
}

export type AuditEvent =
  | HttpEvent
  | FtpEvent
  | TelnetEvent
  | DnsEvent
  | SmtpEvent
  | Pop3Event

export interface AlertRecord {
  id: string
  timestamp: string
  level: AlertLevel
  ruleName: string
  message: string
  auditEventId: string
  protocol: ProtocolType
}

export interface DashboardStats {
  totalEvents: number
  protocolCounts: Record<string, number>
  alertCounts: Record<string, number>
}
