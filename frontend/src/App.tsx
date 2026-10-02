import { FormEvent, useEffect, useState } from 'react'
import { Activity, ArrowRight, CarFront, Gauge, Layers3, LogIn, Menu, Orbit, Radio, ShieldCheck, Sparkles, X } from 'lucide-react'
import { authApi, operationsApi } from './api'
import type { QueueEntry, Resource, ServiceRequest, ServiceType } from './types'

type OperationsData = { requests: ServiceRequest[]; queue: QueueEntry[]; resources: Resource[]; serviceTypes: ServiceType[] }

const emptyData: OperationsData = { requests: [], queue: [], resources: [], serviceTypes: [] }

function App() {
  const [entered, setEntered] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [authError, setAuthError] = useState('')
  const [data, setData] = useState<OperationsData>(emptyData)
  const [loading, setLoading] = useState(false)
  const [dataError, setDataError] = useState('')

  useEffect(() => {
    if (!entered || !localStorage.getItem('optiserve-token')) return
    setLoading(true)
    Promise.all([
      operationsApi.getServiceRequests(),
      operationsApi.getQueue(),
      operationsApi.getResources(),
      operationsApi.getServiceTypes(),
    ]).then(([requests, queue, resources, serviceTypes]) => {
      setData({ requests, queue, resources, serviceTypes })
      setDataError('')
    }).catch(() => setDataError('Connect your authenticated session to view live operations.'))
      .finally(() => setLoading(false))
  }, [entered])

  async function handleLogin(event: FormEvent) {
    event.preventDefault()
    setAuthError('')
    try {
      const response = await authApi.login(email, password)
      localStorage.setItem('optiserve-token', response.accessToken)
      setLoginOpen(false)
      setEntered(true)
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Authentication failed. Check your credentials.')
    }
  }

  return (
    <main className={entered ? 'app-frame entered' : 'app-frame'}>
      {!entered ? <Landing onExplore={() => setEntered(true)} onLogin={() => setLoginOpen(true)} /> : (
        <div className="workspace">
          <aside className={drawerOpen ? 'sidebar open' : 'sidebar'}>
            <div className="brand"><Orbit size={20} /><span>OPTISERVE</span><button className="icon-button mobile-only" onClick={() => setDrawerOpen(false)} aria-label="Close navigation"><X size={18} /></button></div>
            <p className="eyebrow">Service intelligence</p>
            <nav>
              {['Overview', 'Service Requests', 'Queue', 'Assignments', 'Resources', 'Service Types'].map((item, index) => <button className={index === 0 ? 'nav-item active' : 'nav-item'} key={item}><span>{['◈', '⌁', '≋', '↗', '▣', '◇'][index]}</span>{item}</button>)}
            </nav>
            <div className="sidebar-foot"><ShieldCheck size={16} /><span>Backend authority online</span></div>
          </aside>
          {drawerOpen && <button className="scrim" onClick={() => setDrawerOpen(false)} aria-label="Close navigation" />}
          <section className="content">
            <header className="topbar"><button className="icon-button mobile-only" onClick={() => setDrawerOpen(true)} aria-label="Open navigation"><Menu size={20} /></button><div><span className="breadcrumb">Operations /</span><h1>Command overview</h1></div><button className="outline-button" onClick={() => { localStorage.removeItem('optiserve-token'); setEntered(false) }}><LogIn size={15} /> Sign out</button></header>
            <Overview data={data} loading={loading} error={dataError} onLogin={() => setLoginOpen(true)} />
          </section>
        </div>
      )}
      {loginOpen && <div className="modal-backdrop"><form className="login-panel" onSubmit={handleLogin}><button className="close-button" type="button" onClick={() => setLoginOpen(false)} aria-label="Close login"><X size={18} /></button><span className="eyebrow">Secure access</span><h2>Enter operations</h2><p>Use an OptiServe account to load live backend data.</p><label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{authError && <div className="error-banner">{authError}</div>}<button className="primary-button full" type="submit">Authenticate <ArrowRight size={16} /></button></form></div>}
    </main>
  )
}

function Landing({ onExplore, onLogin }: { onExplore: () => void; onLogin: () => void }) {
  return <section className="landing"><div className="ambient ambient-one" /><div className="ambient ambient-two" /><header className="landing-nav"><div className="brand"><Orbit size={20} /><span>OPTISERVE</span></div><button className="text-button" onClick={onLogin}>Open dashboard <ArrowRight size={15} /></button></header><div className="hero-copy"><span className="eyebrow"><span className="signal-dot" /> Automotive service intelligence</span><h1>Every service.<br /><em>In perfect sync.</em></h1><p>Orchestrate requests, resources, queues, and workflows through one intelligent operations platform.</p><div className="hero-actions"><button className="primary-button" onClick={onExplore}>Explore operations <ArrowRight size={16} /></button><button className="ghost-button" onClick={onLogin}>Open dashboard</button></div></div><div className="hero-object" aria-label="Abstract automotive service orchestration visualization"><div className="orbit orbit-large" /><div className="orbit orbit-small" /><div className="core"><CarFront size={58} strokeWidth={1} /></div><div className="float-panel panel-top"><span>LIVE SYSTEM</span><strong>Coordinated</strong><small>workflow state</small></div><div className="float-panel panel-bottom"><span>RESOURCE FIELD</span><strong>Ready / 04</strong><small>capabilities aligned</small></div></div><div className="scroll-cue"><span /> Scroll to enter command view</div><div className="workflow-strip"><span>REQUEST</span><i /> <span>WORKFLOW</span><i /> <span>QUEUE</span><i /> <span>RESOURCE</span><i /> <span>ASSIGNMENT</span></div></section>
}

function Overview({ data, loading, error, onLogin }: { data: OperationsData; loading: boolean; error: string; onLogin: () => void }) {
  const stats = [{ label: 'Requests', value: data.requests.length, icon: Layers3 }, { label: 'Queued stages', value: data.queue.length, icon: Activity }, { label: 'Available resources', value: data.resources.filter((resource) => resource.status === 'AVAILABLE').length, icon: Gauge }, { label: 'Service types', value: data.serviceTypes.length, icon: Sparkles }]
  return <div className="overview"><div className="overview-intro"><div><span className="eyebrow">Live operational surface</span><h2>Keep the bays<br /><em>in motion.</em></h2></div><div className="status-line"><Radio size={15} /> Backend-authoritative view <span className="status-light" /></div></div>{!localStorage.getItem('optiserve-token') && <div className="auth-callout"><div><strong>Authentication required for live operations</strong><p>The overview is ready. Connect to the Spring API to populate current requests, queue entries, resources, and service types.</p></div><button className="primary-button" onClick={onLogin}>Sign in <ArrowRight size={16} /></button></div>}{error && <div className="error-banner">{error}</div>}<div className="stat-grid">{stats.map(({ label, value, icon: Icon }) => <div className="stat" key={label}><Icon size={17} /><span>{label}</span><strong>{loading ? '—' : value.toString().padStart(2, '0')}</strong></div>)}</div><div className="operations-grid"><section className="board"><div className="section-heading"><div><span className="eyebrow">Queue field</span><h3>Current flow</h3></div><span className="live-tag">{data.queue.length ? 'LIVE' : 'NO STAGES'}</span></div>{data.queue.length ? data.queue.slice(0, 5).map((entry) => <div className="queue-row" key={entry.id}><span className={`priority ${entry.priority.toLowerCase()}`}>{entry.priority}</span><strong>{entry.vehicleRegistrationNumber}</strong><span>{entry.serviceTypeName}</span><span className="muted">{entry.stageStatus}</span></div>) : <EmptyState title="No stages are currently queued" detail="Queue entries will appear here when eligible workflow stages are admitted by the backend." />}</section><section className="board resource-board"><div className="section-heading"><div><span className="eyebrow">Resource field</span><h3>Capability map</h3></div><span className="live-tag">{data.resources.length ? 'SYNCED' : 'AWAITING DATA'}</span></div>{data.resources.length ? data.resources.slice(0, 4).map((resource) => <div className="resource-row" key={resource.id}><span className={`resource-light ${resource.status.toLowerCase()}`} /><strong>{resource.name}</strong><span>{resource.compatibleServiceTypes.length} capabilities</span><span className="muted">{resource.status}</span></div>) : <EmptyState title="No resources configured" detail="Resources and their compatibility will resolve here from the operational API." />}</section></div></div>
}

function EmptyState({ title, detail }: { title: string; detail: string }) { return <div className="empty-state"><span className="empty-mark">+</span><strong>{title}</strong><p>{detail}</p></div> }

export default App