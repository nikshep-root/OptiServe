export type PriorityClass = 'CRITICAL' | 'URGENT' | 'APPOINTMENT' | 'NORMAL'
export type ServiceRequestStatus = string
export type ServiceWorkflowStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
export type ServiceStageStatus = 'PENDING' | 'ELIGIBLE' | 'QUEUED' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type QueueEntryStatus = string
export type ResourceStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE'

export interface ServiceRequestStage {
  id: string
  sequenceNumber: number
  serviceTypeId: string
  serviceTypeName: string
  status: ServiceStageStatus
  assignmentId?: string | null
}

export interface ServiceRequest {
  id: string
  vehicleId: string
  vehicleRegistrationNumber: string
  priority: PriorityClass
  appointmentTime?: string | null
  status: ServiceRequestStatus
  workflowId: string
  workflowStatus: ServiceWorkflowStatus
  stages: ServiceRequestStage[]
}

export interface QueueEntry {
  id: string
  stageId: string
  workflowId: string
  serviceRequestId: string
  vehicleId: string
  vehicleRegistrationNumber: string
  serviceTypeId: string
  serviceTypeName: string
  priority: PriorityClass
  queueEntryTime: string
  status: QueueEntryStatus
  stageStatus: ServiceStageStatus
}

export interface CompatibleServiceType {
  id: string
  name: string
}

export interface Resource {
  id: string
  name: string
  status: ResourceStatus
  compatibleServiceTypes: CompatibleServiceType[]
  createdAt: string
  updatedAt: string
}

export interface ServiceType {
  id: string
  name: string
  description: string
  defaultServiceDurationSeconds: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface AuthTokenResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
}

export type AssignmentNextResult = 'ASSIGNED' | 'NO_QUEUED_STAGE' | 'NO_COMPATIBLE_RESOURCE'

export interface AssignmentNextResponse {
  result: AssignmentNextResult
  assignmentId?: string | null
  vehicleRegistrationNumber?: string | null
  serviceTypeName?: string | null
  priority?: PriorityClass | null
  resourceName?: string | null
  assignmentStatus?: string | null
}