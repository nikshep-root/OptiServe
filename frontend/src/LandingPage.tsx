import { lazy, Suspense, useEffect } from 'react'
import { ArrowDown, ArrowRight, Orbit, Timer, Wrench, Boxes, Route } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

const VehicleExperience = lazy(() => import('./VehicleExperience'))

export default function LandingPage() {
  const navigate = useNavigate()

  useEffect(() => {
    const previousRestoration = window.history.scrollRestoration
    window.history.scrollRestoration = 'manual'
    window.scrollTo(0, 0)
    return () => { window.history.scrollRestoration = previousRestoration }
  }, [])

  return (
    <main className="landing-shell chapter-experience">
      <header className="landing-shell-header">
        <button className="landing-brand" onClick={() => navigate('/')} aria-label="OptiServe home">
          <span className="landing-brand-mark"><Orbit size={18} /></span>
          <span>OPTISERVE</span>
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
          <h2 id="separation-title">Every component.<br /><em>In position.</em></h2>
          <p>Separate the serviceable elements without losing the whole. The concept car exposes four wheel assemblies and named body, headlight, chrome, and trim meshes for a controlled inspection view.</p>
        </div>
        <div className="chapter-index"><span>02</span><i /><span>COMPONENT SEPARATION</span></div>
      </section>

      <section className="chapter-stage chapter-stage-assembly" aria-labelledby="assembly-title">
        <div className="chapter-copy chapter-copy-right">
          <span className="landing-kicker">03 / Coordinated return</span>
          <h2 id="assembly-title">Back to one<br /><em>clear signal.</em></h2>
          <p>When every stage knows its place, the whole operation moves with quiet precision.</p>
        </div>
        <div className="chapter-index"><span>03</span><i /><span>COMPONENT ASSEMBLY</span></div>
      </section>

      <section className="chapter-stage chapter-stage-intelligence" id="intelligence" aria-labelledby="intelligence-title">
        <div className="chapter-copy chapter-copy-intelligence"><span className="landing-kicker">04 / Service intelligence</span><h2 id="intelligence-title">The work behind<br /><em>the movement.</em></h2><p>One operational field for the decisions that keep a workshop in flow.</p></div>
        <div className="intelligence-grid"><article><Route size={18} /><span>01</span><h3>Service stages</h3><p>See every vehicle's path from intake to handover.</p></article><article><Timer size={18} /><span>02</span><h3>Queue visibility</h3><p>Understand waiting stages without invented estimates.</p></article><article><Boxes size={18} /><span>03</span><h3>Resource coordination</h3><p>Match capability and availability at the right moment.</p></article><article><Wrench size={18} /><span>04</span><h3>Operational control</h3><p>Let the authoritative scheduler guide the next move.</p></article></div>
        <div className="chapter-index"><span>04</span><i /><span>SERVICE INTELLIGENCE</span></div>
      </section>

      <section className="chapter-stage chapter-stage-final" aria-labelledby="final-title">
        <span className="landing-kicker"><span className="landing-signal" /> OptiServe operations</span>
        <h2 id="final-title">Your workshop.<br /><em>In control.</em></h2>
        <p>Step into the backend-authoritative operations console.</p>
        <button className="landing-primary-cta" onClick={() => navigate('/dashboard')}>Enter dashboard <ArrowRight size={16} /></button>
        <div className="chapter-index"><span>05</span><i /><span>OPERATIONS CONSOLE</span></div>
      </section>
    </main>
  )
}
