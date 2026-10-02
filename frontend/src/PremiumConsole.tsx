import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { Activity, ArrowUpRight, Boxes, CarFront, CheckCircle2, CircleGauge, Layers3, Orbit, RefreshCw, Sparkles, Timer, Wrench, Zap } from 'lucide-react'
import { operationsApi } from './api'
import type { AssignmentNextResponse, QueueEntry, Resource, ServiceRequest, ServiceType } from './types'

type View = 'overview' | 'requests' | 'queue' | 'assignments' | 'resources' | 'types'
type Data = { requests: ServiceRequest[]; queue: QueueEntry[]; resources: Resource[]; types: ServiceType[] }
const blank: Data = { requests: [], queue: [], resources: [], types: [] }
const navigation: Array<{ id: View; label: string; icon: typeof Activity }> = [
  { id: 'overview', label: 'Overview', icon: CircleGauge },
  { id: 'requests', label: 'Service requests', icon: CarFront },
  { id: 'queue', label: 'Queue', icon: Timer },
  { id: 'assignments', label: 'Assignments', icon: Wrench },
  { id: 'resources', label: 'Resources', icon: Boxes },
  { id: 'types', label: 'Service types', icon: Layers3 },
]

export default function PremiumConsole({ onSignOut }: { onSignOut: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const view = (location.pathname.slice(1) || 'overview') as View
  const [assignment, setAssignment] = useState<AssignmentNextResponse | null>(null)

  const requests = useQuery({ queryKey: ['service-requests'], queryFn: operationsApi.getServiceRequests })
  const queue = useQuery({ queryKey: ['queue'], queryFn: operationsApi.getQueue })
  const resources = useQuery({ queryKey: ['resources'], queryFn: operationsApi.getResources })
  const types = useQuery({ queryKey: ['service-types'], queryFn: operationsApi.getServiceTypes })
  const data: Data = { requests: requests.data ?? blank.requests, queue: queue.data ?? blank.queue, resources: resources.data ?? blank.resources, types: types.data ?? blank.types }
  const loading = requests.isLoading || queue.isLoading || resources.isLoading || types.isLoading
  const syncError = requests.error || queue.error || resources.error || types.error ? 'Backend sync unavailable. Confirm the API and bearer token are configured.' : ''
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['service-requests'] }).then(() => queryClient.invalidateQueries({ queryKey: ['queue'] })).then(() => queryClient.invalidateQueries({ queryKey: ['resources'] })).then(() => queryClient.invalidateQueries({ queryKey: ['service-types'] }))

  async function assignNext() {
    try {
      const result = await operationsApi.assignNext()
      setAssignment(result)
      refresh()
    } catch { setAssignment({ result: 'NO_COMPATIBLE_RESOURCE' }) }
  }

  const active = navigation.find((item) => item.id === view) ?? navigation[0]
  return <main className="premium-app">
    <div className="aurora aurora-one" />
    <div className="aurora aurora-two" />
    <div className="grain" />
    <header className="site-header">
      <div className="premium-brand"><span className="brand-orbit"><Orbit size={18} /></span><span>OPTISERVE</span></div>
      <nav className="center-nav" aria-label="Primary navigation">{navigation.map(({ id, label, icon: Icon }) => <button key={id} className={view === id ? 'center-nav-item active' : 'center-nav-item'} onClick={() => navigate(`/${id}`)}><Icon size={14} /><span>{label}</span></button>)}</nav>
      <div className="site-actions"><span className="sync-badge"><span className="pulse" /> {syncError ? 'SYNC PAUSED' : 'LIVE SYNC'}</span><button className="refresh-button" onClick={refresh} aria-label="Refresh data"><RefreshCw size={16} className={loading ? 'spinning' : ''} /></button><button className="signout-button" onClick={onSignOut}>Sign out <ArrowUpRight size={13} /></button></div>
    </header>
    <section className="premium-content">
      <header className="page-context"><span className="top-kicker">Operations studio / {active.label}</span><span className="context-status"><span className="pulse" /> {syncError ? 'Connection needs attention' : 'Backend authoritative'}</span></header>
      {syncError && <div className="premium-error"><Activity size={16} /><span>{syncError}</span></div>}
      {view === 'overview' && <Overview data={data} loading={loading} onAssign={() => void assignNext()} />}
      {view === 'requests' && <ListView title="Service request flow" kicker="01 / Intake" items={data.requests.map((item) => ({ primary: item.vehicleRegistrationNumber, secondary: item.priority, tertiary: `${item.stages.length} stages`, state: item.status }))} empty="No service requests returned by the backend." loading={loading} />}
      {view === 'queue' && <ListView title="Waiting stages" kicker="02 / Queue" items={data.queue.map((item) => ({ primary: item.vehicleRegistrationNumber, secondary: item.priority, tertiary: item.serviceTypeName, state: item.stageStatus }))} empty="No stages are currently queued." loading={loading} />}
      {view === 'resources' && <ListView title="Resource capability map" kicker="04 / Resources" items={data.resources.map((item) => ({ primary: item.name, secondary: item.status, tertiary: `${item.compatibleServiceTypes.length} capabilities`, state: item.status }))} empty="No service resources configured." loading={loading} />}
      {view === 'types' && <ListView title="Service catalogue" kicker="05 / Definitions" items={data.types.map((item) => ({ primary: item.name, secondary: item.active ? 'ACTIVE' : 'INACTIVE', tertiary: `${Math.round(item.defaultServiceDurationSeconds / 60)} min default`, state: item.description }))} empty="No service types configured." loading={loading} />}
      {view === 'assignments' && <AssignmentView result={assignment} onAssign={() => void assignNext()} />}
    </section>
  </main>
}

function Overview({ data, loading, onAssign }: { data: Data; loading: boolean; onAssign: () => void }) {
  const cards = [{ label: 'Service requests', value: data.requests.length, icon: CarFront }, { label: 'Queued stages', value: data.queue.length, icon: Timer }, { label: 'Available resources', value: data.resources.filter((item) => item.status === 'AVAILABLE').length, icon: Boxes }, { label: 'Active service types', value: data.types.filter((item) => item.active).length, icon: Sparkles }]
  return <div className="view-body">
    <section className="command-hero">
      <div className="hero-copy">
        <span className="section-kicker"><span className="kicker-star">✦</span> Command center / 00</span>
        <h2>Precision work,<br /><em>beautifully timed.</em></h2>
        <p>One intelligent surface for every vehicle, stage, queue, and resource in motion.</p>
        <div className="hero-meta"><span><span className="pulse" /> System coherent</span><span>Backend-authoritative</span></div>
        <button className="accent-button" onClick={onAssign}><Wrench size={16} /> Assign next stage <ArrowUpRight size={16} /></button>
      </div>
      <div className="orbital-stage" aria-hidden="true"><div className="orbital-shadow" /><div className="orbital-ring ring-one" /><div className="orbital-ring ring-two" /><div className="orbital-ring ring-three" /><div className="orbital-core"><span className="core-glint" /><Orbit size={38} strokeWidth={1.25} /></div><div className="orbit-chip chip-top"><Zap size={12} /><span>FLOW OPTIMISED</span></div><div className="orbit-chip chip-bottom"><span className="chip-status" /> LIVE SIGNAL</div></div>
      <div className="hero-story" aria-label="Operational intelligence summary"><div className="story-mark"><Sparkles size={15} /></div><div><span>YOUR OPERATIONS</span><strong>Always in flow</strong><small>One clear signal from intake to handover.</small></div><ArrowUpRight size={15} /></div>
      <div className="hero-telemetry" aria-label="System highlights"><div><span>ORCHESTRATION</span><strong>Adaptive</strong><small>Scheduler ready</small></div><div><span>VISIBILITY</span><strong>Unified</strong><small>Live workflow field</small></div><div><span>CONTROL</span><strong>Human-first</strong><small>Built for your team</small></div></div>
    </section>
    <div className="metric-grid">{cards.map(({ label, value, icon: Icon }, index) => <div className="metric-card" style={{ animationDelay: `${index * 80}ms` }} key={label}><span className="metric-sheen" /><div className="metric-icon"><Icon size={17} /></div><span>{label}</span><strong>{loading ? '—' : value.toString().padStart(2, '0')}</strong><small>backend-derived <ArrowUpRight size={11} /></small></div>)}</div>
    <div className="lower-grid"><section className="glass-panel wide-panel"><div className="panel-heading"><div><span className="section-kicker">Queue field / live</span><h3>Next in service</h3></div><span className="quiet-state"><span className="pulse" /> {data.queue.length ? 'STAGED' : 'CLEAR'}</span></div>{data.queue.slice(0, 4).map((item) => <div className="operation-row" key={item.id}><span className={`priority-dot ${item.priority.toLowerCase()}`} /><div><strong>{item.vehicleRegistrationNumber}</strong><small>{item.serviceTypeName}</small></div><span className="row-state">{item.stageStatus}</span><ArrowUpRight className="row-arrow" size={15} /></div>)}{!data.queue.length && <Empty text="The queue is clear. Eligible stages will appear here." />}</section><section className="glass-panel"><div className="panel-heading"><div><span className="section-kicker">Resource field</span><h3>Bay readiness</h3></div><Activity size={16} /></div>{data.resources.slice(0, 4).map((item) => <div className="resource-mini" key={item.id}><span className={`resource-dot ${item.status.toLowerCase()}`} /><strong>{item.name}</strong><small>{item.status}</small></div>)}{!data.resources.length && <Empty text="No resources configured." />}</section></div>
  </div>
}

function ListView({ title, kicker, items, empty, loading }: { title: string; kicker: string; items: Array<{ primary: string; secondary: string; tertiary: string; state: string }>; empty: string; loading: boolean }) { return <div className="view-body"><div className="page-heading"><div><span className="section-kicker">{kicker}</span><h2>{title}</h2><p>Live records returned by the OptiServe backend.</p></div><div className="heading-orb" aria-hidden="true"><span /><span /><span /></div></div><section className="glass-panel record-panel">{loading ? <Empty text="Synchronising operational records..." /> : items.length ? items.map((item, index) => <div className="record-row" key={`${item.primary}-${index}`}><span className="record-index">{String(index + 1).padStart(2, '0')}</span><strong>{item.primary}</strong><span className="record-secondary">{item.secondary}</span><span>{item.tertiary}</span><span className="row-state">{item.state}</span><ArrowUpRight className="record-arrow" size={15} /></div>) : <Empty text={empty} />}</section></div> }

function AssignmentView({ result, onAssign }: { result: AssignmentNextResponse | null; onAssign: () => void }) { const label = result?.result === 'ASSIGNED' ? 'Stage assigned' : result?.result === 'NO_QUEUED_STAGE' ? 'No queued stage' : result?.result === 'NO_COMPATIBLE_RESOURCE' ? 'No compatible resource' : 'Ready to orchestrate'; return <div className="view-body"><div className="page-heading"><div><span className="section-kicker">03 / Control center</span><h2>Assignment console</h2><p>Ask the backend scheduler for the next compatible resource.</p></div><div className="heading-orb assignment-orb" aria-hidden="true"><span /><span /><span /></div></div><section className="assignment-card glass-panel"><div className="assignment-beam" /><div className="assignment-icon"><Wrench size={25} /></div><span className="section-kicker">Scheduler response</span><h3>{label}</h3>{result?.result === 'ASSIGNED' && <div className="assignment-detail"><strong>{result.vehicleRegistrationNumber}</strong><span>{result.serviceTypeName} / {result.resourceName}</span></div>}<button className="accent-button" onClick={onAssign}><Wrench size={16} /> Assign next stage <ArrowUpRight size={16} /></button></section></div> }
function Empty({ text }: { text: string }) { return <div className="empty-premium"><CheckCircle2 size={18} /><span>{text}</span></div> }
