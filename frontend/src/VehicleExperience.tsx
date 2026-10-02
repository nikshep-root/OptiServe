import { Suspense, useEffect, useRef, useState } from 'react'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { Html, useGLTF } from '@react-three/drei'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import type { Group, Object3D } from 'three'
import { Box3, Color, MathUtils, Mesh, MeshPhysicalMaterial, Vector3 } from 'three'

gsap.registerPlugin(ScrollTrigger)

const modelPath = '/models/free_concept_car_038_-_public_domain_cc0.glb'
const chapterProgress = { value: 0 }
let sceneUsers = 0
let sceneTriggers: ScrollTrigger[] = []

function findNode(root: Object3D, name: string) {
  return root.getObjectByName(name)
}

function VehicleModel({ reducedMotion }: { reducedMotion: boolean }) {
  const model = useGLTF(modelPath)
  const group = useRef<Group>(null)
  const body = useRef<Object3D | null>(null)
  const wheelNodes = useRef<Object3D[]>([])
  const bodyPanelNodes = useRef<Object3D[]>([])
  const headlightNodes = useRef<Object3D[]>([])
  const detailNodes = useRef<Object3D[]>([])
  const { pointer, size } = useThree()
  const targetPosition = useRef(new Vector3())
  const basePositions = useRef(new Map<Object3D, Vector3>())
  const baseRotations = useRef(new Map<Object3D, Vector3>())
  const cameraDistance = useRef(5.2)

  useEffect(() => {
    body.current = findNode(model.scene, 'RootNode') ?? null
    wheelNodes.current = ['Empty_FL_Wheels', 'Empty_FL_Wheels.001', 'Empty_FL_Wheels.002', 'Empty_FL_Wheels.003'].map((name) => findNode(model.scene, name)).filter((node): node is Object3D => Boolean(node))
    bodyPanelNodes.current = []
    headlightNodes.current = []
    model.scene.traverse((object) => {
      if (object.name.includes('body_color_supra')) bodyPanelNodes.current.push(object)
      if (object.name.includes('headlightCovers')) headlightNodes.current.push(object)
    })
    detailNodes.current = []
    model.scene.traverse((object) => {
      if (object.name.includes('chrome') || object.name.includes('plastic') || object.name.includes('glass')) detailNodes.current.push(object)
    })
    const nodes = [body.current, ...wheelNodes.current, ...bodyPanelNodes.current, ...headlightNodes.current, ...detailNodes.current].filter((node): node is Object3D => Boolean(node))
    nodes.forEach((node) => { basePositions.current.set(node, node.position.clone()); baseRotations.current.set(node, new Vector3(node.rotation.x, node.rotation.y, node.rotation.z)) })
    const bounds = new Box3().setFromObject(model.scene)
    const size = bounds.getSize(new Vector3())
    const visualWidth = Math.max(size.x, size.z) * .012
    cameraDistance.current = Math.max(5.25, visualWidth / (2 * Math.tan((32 * Math.PI / 180) / 2)) * .84)
    model.scene.traverse((object) => {
      if (object.name.startsWith('Text.') || object.name.includes('Text.')) object.visible = false
      if (!(object instanceof Mesh)) return
      object.castShadow = true
      object.receiveShadow = true
      const sourceMaterial = Array.isArray(object.material) ? object.material[0] : object.material
      const materialName = sourceMaterial.name.toLowerCase()
      const isBody = materialName.includes('body_color') || object.name.includes('body_color_supra')
      const isGlass = materialName.includes('glass') || materialName.includes('window')
      const isWheel = materialName.includes('rubber') || materialName.includes('tire') || materialName.includes('rim')
      const isLight = materialName.includes('headlight')
      const material = new MeshPhysicalMaterial({
        color: isBody ? new Color('#f1f4f4') : isGlass ? new Color('#06131d') : isWheel ? new Color('#171e24') : isLight ? new Color('#c8f8ff') : new Color(sourceMaterial.color),
        metalness: isBody ? .82 : isWheel ? .78 : isGlass ? .2 : .55,
        roughness: isBody ? .17 : isWheel ? .3 : isGlass ? .12 : .35,
        clearcoat: isBody ? 1 : isGlass ? .35 : 0,
        clearcoatRoughness: isBody ? .08 : .2,
        transparent: isGlass || isBody,
        opacity: isGlass ? .82 : isBody ? .06 : 1,
        envMapIntensity: isBody ? 2.1 : 1.15,
        emissive: isBody ? new Color('#4b5d62') : new Color('#000000'),
        emissiveIntensity: isBody ? .38 : 0,
      })
      object.material = material
    })
  }, [model.scene])

  useFrame(({ camera, clock }) => {
    if (!group.current) return
    const progress = chapterProgress.value
    const mechanics = MathUtils.smoothstep(progress, .22, .46)
    const bodyReveal = MathUtils.smoothstep(progress, .42, .7)
    const separation = Math.max(0, 1 - Math.abs(progress - .36) / .2)
    const completion = MathUtils.smoothstep(progress, .88, 1)
    const elapsed = reducedMotion ? 0 : clock.getElapsedTime()
    const pointerX = reducedMotion ? 0 : pointer.x * .16
    const pointerY = reducedMotion ? 0 : pointer.y * .08
    const idle = reducedMotion ? 0 : Math.sin(elapsed * .7) * .018

    const cameraAngle = progress < .25 ? .2 : progress < .55 ? -.75 : 1.42
    const vehicleAngle = progress < .25 ? .58 : progress < .55 ? .38 : .12
    group.current.rotation.y = MathUtils.lerp(group.current.rotation.y, vehicleAngle + pointerX, .06)
    group.current.rotation.x = MathUtils.lerp(group.current.rotation.x, pointerY, .06)
    group.current.position.y = MathUtils.lerp(group.current.position.y, -.22 + idle + separation * .12, .06)
    const compactViewport = size.width < 780
    const chapterOffsetX = compactViewport
      ? (progress < .42 ? .42 : progress < .63 ? -.32 : progress < .88 ? .42 : .05)
      : (progress < .42 ? .72 : progress < .63 ? -.52 : progress < .88 ? .72 : .05)
    targetPosition.current.set(chapterOffsetX + pointerX * .35, 0, 0)
    group.current.position.x = MathUtils.lerp(group.current.position.x, targetPosition.current.x, .06)

    if (body.current) {
      const base = basePositions.current.get(body.current)
      if (base) body.current.position.y = MathUtils.lerp(body.current.position.y, base.y + (1 - bodyReveal) * .18 + separation * .16, .08)
    }
    wheelNodes.current.forEach((wheel, index) => {
      const base = basePositions.current.get(wheel)
      if (!base) return
      const side = index % 2 === 0 ? -1 : 1
      const axle = index < 2 ? 1 : -1
      const wheelEntry = MathUtils.smoothstep(progress, .05 + index * .035, .32 + index * .035)
      wheel.position.x = MathUtils.lerp(wheel.position.x, base.x + side * (1 - wheelEntry) * .22 + side * separation * .5, .08)
      wheel.position.z = MathUtils.lerp(wheel.position.z, base.z + axle * (1 - wheelEntry) * .14 + axle * separation * .22, .08)
      wheel.position.y = MathUtils.lerp(wheel.position.y, base.y - (1 - wheelEntry) * .16 - separation * .12, .08)
      const baseRotation = baseRotations.current.get(wheel)
      wheel.rotation.z = MathUtils.lerp(wheel.rotation.z, (baseRotation?.z ?? 0) + separation * side * .07, .08)
    })
    bodyPanelNodes.current.forEach((panel, index) => {
      const base = basePositions.current.get(panel)
      if (!base) return
      const side = index % 2 === 0 ? -1 : 1
      const panelEntry = MathUtils.smoothstep(progress, .28 + index * .018, .7 + index * .018)
      const panelMaterial = panel instanceof Mesh && panel.material instanceof MeshPhysicalMaterial ? panel.material : null
      if (panelMaterial) panelMaterial.opacity = MathUtils.lerp(.92, 1, MathUtils.lerp(panelEntry, 1, completion))
      panel.position.x = MathUtils.lerp(panel.position.x, base.x + side * (1 - panelEntry) * .12 + side * separation * .12, .08)
      panel.position.y = MathUtils.lerp(panel.position.y, base.y + (1 - panelEntry) * .1 + separation * (.12 + (index % 3) * .035), .08)
    })
    headlightNodes.current.forEach((headlight, index) => {
      const base = basePositions.current.get(headlight)
      if (!base) return
      const lightEntry = MathUtils.smoothstep(progress, .62, .88)
      headlight.position.z = MathUtils.lerp(headlight.position.z, base.z + (1 - lightEntry) * .32 + separation * (.18 + index * .04), .08)
    })
    detailNodes.current.forEach((detail, index) => {
      const base = basePositions.current.get(detail)
      if (!base) return
      const detailEntry = MathUtils.smoothstep(progress, .58 + index * .006, .94 + index * .006)
      detail.position.y = MathUtils.lerp(detail.position.y, base.y + (1 - detailEntry) * .28, .08)
    })

    if (completion > .98) {
      group.current.position.set(.05, -.22, 0)
      group.current.rotation.set(0, .12, 0)
      body.current?.position.copy(basePositions.current.get(body.current) ?? body.current.position)
      wheelNodes.current.forEach((wheel) => {
        wheel.position.copy(basePositions.current.get(wheel) ?? wheel.position)
        const rotation = baseRotations.current.get(wheel)
        wheel.rotation.set(rotation?.x ?? 0, rotation?.y ?? 0, rotation?.z ?? 0)
      })
      bodyPanelNodes.current.forEach((panel) => { panel.position.copy(basePositions.current.get(panel) ?? panel.position); if (panel instanceof Mesh && panel.material instanceof MeshPhysicalMaterial) panel.material.opacity = 1 })
      headlightNodes.current.forEach((light) => light.position.copy(basePositions.current.get(light) ?? light.position))
      detailNodes.current.forEach((detail) => detail.position.copy(basePositions.current.get(detail) ?? detail.position))
    }

    const finalFraming = MathUtils.smoothstep(progress, .68, .92)
    const inspectionDolly = MathUtils.lerp(cameraDistance.current, cameraDistance.current * .78, mechanics)
    const detailDolly = MathUtils.lerp(inspectionDolly, cameraDistance.current * 1.6, finalFraming)
    const responsiveDolly = detailDolly * (compactViewport ? 1.32 : 1)
    // Keep the camera aimed at the shared scene origin. Tracking the vehicle's chapter
    // offset here would cancel the intended left/right composition and put copy over it.
    const cameraTargetX = 0
    camera.position.x = MathUtils.lerp(camera.position.x, responsiveDolly * Math.sin(cameraAngle) + pointerX, .045)
    camera.position.y = MathUtils.lerp(camera.position.y, responsiveDolly * .24 + progress * .35 + pointerY, .045)
    camera.position.z = MathUtils.lerp(camera.position.z, responsiveDolly * Math.cos(cameraAngle), .045)
    camera.lookAt(cameraTargetX, .05 + separation * .12, 0)
  })

  return <group ref={group} scale={.012} position={[0, -.22, 0]}><primitive object={model.scene} /></group>
}

function SceneLoading() {
  return <Html center><div className="vehicle-scene-status">Loading vehicle experience<span /></div></Html>
}

function WebGLFallback() {
  return <div className="vehicle-scene-fallback"><strong>Vehicle experience unavailable</strong><span>Your browser does not support WebGL.</span></div>
}

function Scene({ reducedMotion }: { reducedMotion: boolean }) {
  const { camera } = useThree()
  useEffect(() => {
    camera.position.set(5.5, 1.7, 5.5)
  }, [camera])
  return <>
    <Suspense fallback={<SceneLoading />}>
      <color attach="background" args={['#0a111b']} />
      <fog attach="fog" args={['#0a111b', 8, 19]} />
      <ambientLight intensity={1.45} />
      <hemisphereLight intensity={2.35} color="#f4fbff" groundColor="#1a2a38" />
      <directionalLight position={[4, 7, 4]} intensity={8.2} color="#ffffff" castShadow shadow-mapSize={[1024, 1024]} />
      <spotLight position={[-5, 3, 3]} intensity={38} angle={.45} penumbra={1} color="#55e4d1" />
      <spotLight position={[5, 2, -3]} intensity={34} angle={.55} penumbra={1} color="#6689ff" />
      <pointLight position={[0, 1.6, 4]} intensity={22} color="#e5faff" />
      <VehicleModel reducedMotion={reducedMotion} />
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -1.02, 0]} receiveShadow><planeGeometry args={[18, 18]} /><meshStandardMaterial color="#050b13" roughness={.62} metalness={.35} /></mesh>
    </Suspense>
  </>
}

function useChapterTimeline(reducedMotion: boolean) {
  useEffect(() => {
    if (!window.location.hash) window.scrollTo(0, 0)
    sceneUsers += 1
    const copies = gsap.utils.toArray<HTMLElement>('.chapter-copy')
    const intelligenceGrid = document.querySelector<HTMLElement>('.intelligence-grid')
    const updateCopyState = (progress: number) => {
      copies.forEach((copy, index) => {
        if (reducedMotion) {
          gsap.set(copy, { opacity: 1, y: 0 })
          return
        }
        const distance = Math.abs(progress - index / 4)
        const opacity = Math.max(0, 1 - distance * 6)
        gsap.set(copy, { opacity, y: (1 - opacity) * 22 })
      })
      if (intelligenceGrid) {
        if (reducedMotion) {
          gsap.set(intelligenceGrid, { opacity: 1, y: 0 })
          return
        }
        const distance = Math.abs(progress - .75)
        const opacity = Math.max(0, 1 - distance * 6)
        gsap.set(intelligenceGrid, { opacity, y: (1 - opacity) * 22 })
      }
    }
    if (reducedMotion) chapterProgress.value = 1
    updateCopyState(chapterProgress.value)
    if (sceneUsers === 1 && !reducedMotion) {
      sceneTriggers = [ScrollTrigger.create({ trigger: '.chapter-experience', start: 'top top', end: 'bottom bottom', scrub: .85, snap: { snapTo: [0, .25, .5, .75, 1], duration: .55, ease: 'power2.out' }, onUpdate: (self) => { chapterProgress.value = self.progress; document.querySelector('.chapter-experience')?.setAttribute('data-scene-progress', self.progress.toFixed(3)); updateCopyState(self.progress) } })]
      ScrollTrigger.refresh()
    }
    return () => {
      sceneUsers -= 1
      if (sceneUsers === 0) { sceneTriggers.forEach((trigger) => trigger.kill()); sceneTriggers = [] }
    }
  }, [reducedMotion])
}

export default function VehicleExperience() {
  const [reducedMotion, setReducedMotion] = useState(false)
  const [webglAvailable, setWebglAvailable] = useState(true)
  useChapterTimeline(reducedMotion)

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    const updateMotion = () => setReducedMotion(mediaQuery.matches)
    updateMotion()
    mediaQuery.addEventListener('change', updateMotion)
    try {
      const canvas = document.createElement('canvas')
      setWebglAvailable(Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl')))
    } catch { setWebglAvailable(false) }
    return () => mediaQuery.removeEventListener('change', updateMotion)
  }, [])

  if (!webglAvailable) return <WebGLFallback />
  return <div className="vehicle-scene-canvas"><Canvas shadows dpr={[1, 1.35]} camera={{ position: [6.2, 1.75, 6.2], fov: 32 }} gl={{ antialias: true, powerPreference: 'high-performance' }}><Scene reducedMotion={reducedMotion} /></Canvas></div>
}

useGLTF.preload(modelPath)
