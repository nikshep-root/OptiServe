import { lazy, Suspense, useEffect } from 'react'
import { ArrowDown, ArrowRight, Timer, Wrench, Boxes, Route, Activity, CircleDot } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

const VehicleExperience = lazy(() => import('./VehicleExperience'))
const brandLogoPath = '/OptiServe%20Automotive%20Tech%20Logo.png'

export default function LandingPage() {
  const navigate = useNavigate()

  useEffect(() => {
    const previousRestoration = window.history.scrollRestoration
    window.history.scrollRestoration = 'manual'
    if (!window.location.hash) {
      const resetScroll = () => window.scrollTo(0, 0)
      resetScroll()
      const frame = requestAnimationFrame(resetScroll)
      const timer = window.setTimeout(resetScroll, 180)
      return () => { cancelAnimationFrame(frame); window.clearTimeout(timer); window.history.scrollRestoration = previousRestoration }
    }
    return () => { window.history.scrollRestoration = previousRestoration }
  }, [])

  return (
    <main className="landing-shell chapter-experience">
      <header className="landing-shell-header">
        <button className="landing-brand" onClick={() => navigate('/')} aria-label="OptiServe home">
          <img className="brand-logo brand-logo-landing" src={brandLogoPath} alt="" draggable={false} />
        </button>
        <nav className="landing-navigation" aria-label="Landing page navigation">
          <a href="#chapters">Experience</a>
          <a href="#intelligence">Intelligence</a>
          <button className="landing-nav-cta" onClick={() => navigate('/dashboard')}>Enter dashboard <ArrowRight size={14} /></button>
        </nav>
      </header>

      <div className="chapter-visual" aria-label="Interactive OptiServe vehicle experience">
        <Suspense fallback={<div className="vehicle-scene-loading">Loading vehicle experience<span /></div>}>
          <VehicleExperience />
        </Suspense>
      </div>

      <section className="chapter-stage chapter-stage-hero" id="chapters" aria-labelledby="landing-title">
        <div className="landing-hero-copy chapter-copy chapter-copy-hero">
          <span className="landing-kicker"><span className="landing-signal" /> Automotive service intelligence</span>
          <h1 id="landing-title">Precision work.<br /><em>Beautifully timed.</em></h1>
          <p>One intelligent operational field for every vehicle, service stage, queue, and resource.</p>
          <div className="hero-cta-row"><button className="landing-primary-cta" onClick={() => document.getElementById('chapter-separation')?.scrollIntoView({ behavior: 'smooth' })}>Explore the experience <ArrowDown size={16} /></button><button className="landing-secondary-cta" onClick={() => navigate('/dashboard')}>Enter dashboard <ArrowRight size={16} /></button></div>
        </div>
        <div className="chapter-index"><span>01</span><i /><span>VEHICLE REVEAL</span></div>
      </section>

      <section className="chapter-stage chapter-stage-separation" id="chapter-separation" aria-labelledby="separation-title">
        <div className="chapter-copy chapter-copy-left">
          <span className="landing-kicker">02 / Inspection view</span>
          <h2 id="separation-title">Every component<br /><em>has a purpose.</em></h2>
          <p>Explore the structure behind every vehicle, from the foundation to the finished body. Four wheel assemblies begin outside the vehicle and move toward their verified positions.</p>
        </div>
        <div className="chapter-index"><span>02</span><i /><span>COMPONENT SEPARATION</span></div>
      </section>

      <section className="chapter-stage chapter-stage-assembly" aria-labelledby="assembly-title">
        <div className="chapter-copy chapter-copy-right chapter-copy-assembly">
          <span className="landing-kicker">03 / Coordinated return</span>
          <h2 id="assembly-title">Built to work<br /><em>as one.</em></h2>
          <p>Independent components. Coordinated movement. Every stage has a place.</p>
          <aside className="assembly-inspection-panel" aria-label="Assembly inspection status">
            <div className="assembly-panel-heading"><Activity size={15} /><span>Assembly inspection</span></div>
            <div className="assembly-progress-row"><span>Body alignment</span><strong>Coordinating</strong></div>
            <div className="assembly-progress-track"><span /></div>
            <div className="assembly-panel-detail"><CircleDot size={13} /><span>Named body, headlight, trim, and wheel groups returning to base positions.</span></div>
          </aside>
        </div>
        <div className="chapter-index"><span>03</span><i /><span>COMPONENT ASSEMBLY</span></div>
      </section>

      <section className="chapter-stage chapter-stage-intelligence" id="intelligence" aria-labelledby="intelligence-title">
        <div className="chapter-copy chapter-copy-intelligence"><span className="landing-kicker">04 / Service intelligence</span><h2 id="intelligence-title">Every stage.<br /><em>In sync.</em></h2><p>From service intake to handover, OptiServe coordinates the operational flow.</p></div>
        <div className="intelligence-grid"><article><Route size={18} /><span>01</span><h3>Service stages</h3><p>See every vehicle's path from intake to handover.</p></article><article><Timer size={18} /><span>02</span><h3>Queue visibility</h3><p>Understand waiting stages without invented estimates.</p></article><article><Boxes size={18} /><span>03</span><h3>Resource coordination</h3><p>Match capability and availability at the right moment.</p></article><article><Wrench size={18} /><span>04</span><h3>Operational control</h3><p>Let the authoritative scheduler guide the next move.</p></article></div>
        <div className="chapter-index"><span>04</span><i /><span>SERVICE INTELLIGENCE</span></div>
      </section>

      <section className="chapter-stage chapter-stage-final" aria-labelledby="final-title">
        <div className="chapter-copy chapter-copy-final">
          <span className="landing-kicker"><span className="landing-signal" /> OptiServe operations</span>
          <h2 id="final-title">Your workshop.<br /><em>In control.</em></h2>
          <p>Step into the backend-authoritative operations console.</p>
          <button className="landing-primary-cta" onClick={() => navigate('/dashboard')}>Enter dashboard <ArrowRight size={16} /></button>
        </div>
        <div className="chapter-index"><span>05</span><i /><span>OPERATIONS CONSOLE</span></div>
      </section>
    </main>
  )
}
