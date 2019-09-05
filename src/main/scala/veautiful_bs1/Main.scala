package veautiful_bs1

import com.wbillingsley.veautiful.{<, Attacher, DElement, ElementComponent, MakeItSo, VNode, ^}
import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.raw.HTMLInputElement

import scala.collection.mutable.ArrayBuffer
import org.scalajs.dom.svg.Circle

import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetIntervalHandle
import scala.util.Random

object Main {

  /** A molecule is a point that has a class so we can see purple ones and green ones */
  case class Molecule(var position: Vec2, ordinary:Boolean = true) {

    /** Moves in a random direction within a container */
    def move(h:Double, within:Rect):Unit = {
      val p2 = within.limit(position + Vec2.randomDir(0.225 * h))
      position = p2
    }
  }

  case class Ring(p:Vec2, r:Double) {
    def randomPoint():Vec2 = {
      Vec2.randomDir(Math.random() * r) + p
    }

    def contains(p2:Vec2):Boolean = {
      (p2 - p).magnitude < r
    }
  }

  case class Rect(p1:Vec2, p2:Vec2) {
    def limit(p:Vec2): Vec2 = {
      Vec2(
        Math.max(p1.x, Math.min(p.x, p2.x)),
        Math.max(p1.y, Math.min(p.y, p2.y))
      )
    }

    def randomPoint():Vec2 = {
      Vec2(
        p1.x + Math.random() * (p2.x - p1.x),
        p1.y + Math.random() * (p2.y - p1.y)
      )
    }
  }

  object Simulation {

    var tick = 0;

    var results = ArrayBuffer.empty[(Int, Int)]

    /** The container molecules can't escape */
    val bounds = Rect(Vec2(0,0), Vec2(640, 480))

    /** The boundary ring for the experiment */
    val boundaryRing = Ring(Vec2(320, 240), 50)

    /** The molecules we measure the diffusion of start within this ring */
    val dropRing = Ring(Vec2(320, 240), 20)

    val molecules:ArrayBuffer[Molecule] = ArrayBuffer.empty

    var numOrdinary = 1000//00

    var numTracked = 200

    var heat:Double = 5

    def outsideBoundary = {
      molecules.filterNot({ m =>
        m.ordinary || boundaryRing.contains(m.position)
      }).size
    }

    def reset():Unit = {
      tick = 0
      results.clear()
      molecules.clear()

      val ord = for { _ <- 0 until numOrdinary } yield Molecule(bounds.randomPoint())
      val tracked = for { _ <- 0 until numTracked } yield Molecule(dropRing.randomPoint(), false)

      molecules.append((ord ++ tracked):_*)
    }

    def step():Unit = {
      for { m <- molecules } m.move(heat, bounds)
      tick += 1

      if (tick % 100 == 0) {
        results.append(tick -> outsideBoundary)
      }
    }

    reset()

  }

  class MoleculeView(m:Molecule) extends VNode {

    var domNode: Option[Circle] = None

    override def attach(): Node = {
      val c = <.circle(
        ^.attr("cx") := m.position.x, ^.attr("cy") := m.position.y, ^.attr("r") := 3,
        ^.cls := (if (m.ordinary) "molecule ordinary" else "molecule tracked")
      ).create().asInstanceOf[Circle]

      domNode = Some(c)
      c
      /*
      val c = dom.document.createElementNS(DElement.svgNS, "circle").asInstanceOf[Circle]
      c.setAttribute("r", "3")
      c.classList.add("molecule")
      if (m.ordinary) {
        c.classList.add("ordinary")
      } else {
        c.classList.add("tracked")
      }

      c
      */

    }

    override def detach(): Unit = {
      domNode = None
    }

    def update(): Unit = {
      for { c <- domNode } {
        c.setAttribute("cx", m.position.x.toString)
        c.setAttribute("cy", m.position.y.toString)
      }
    }

  }

  case object SimulationView extends ElementComponent(<.div()) {
    // And these variables are used to keep track of how long it took us to render ourselves
    var last:Long = System.currentTimeMillis()
    var dt:Long = 0

    var moleculeNodes = Simulation.molecules.map { m => new MoleculeView(m) }

    def resetMoleculeViews() {
      moleculeNodes = Simulation.molecules.map { m => new MoleculeView(m) }
    }

    /**
     * The SVG that will contain the asteroid field
     */
    def svg:DElement = <.svg.attrs(
      ^.attr("viewbox") := s"${Simulation.bounds.p1.x} ${Simulation.bounds.p1.y} ${Simulation.bounds.p2.x} ${Simulation.bounds.p2.y}",
      ^.attr("width") := s"${Simulation.bounds.p2.x - Simulation.bounds.p1.x}",
      ^.attr("height") := s"${Simulation.bounds.p2.y - Simulation.bounds.p1.y}"
    )

    /** Turns an asteroid into an SVG DElement */
    def svgMolecule(m:Molecule):VNode = {
      <("circle", ns=DElement.svgNS, u=Random.nextString(7))(
        ^.attr("cx") := m.position.x, ^.attr("cy") := m.position.y, ^.attr("r") := 3,
        ^.cls := (if (m.ordinary) "molecule ordinary" else "molecule tracked")
      )
    }

    /** Turns an asteroid into an SVG DElement */
    def svgRing(m:Ring):VNode = {
      <.circle(^.attr("cx") := m.p.x, ^.attr("cy") := m.p.y, ^.attr("r") := m.r, ^.cls := "ring")
    }

    def table():VNode = <.div(^.cls := "results col-lg overflow-auto",
      <.table(^.cls := "table",
        <.thead(
          <.tr(
            <.th(^.attr("scope") := "col", "Tick"),
            <.th(^.attr("scope") := "col", "Outside boundary")
          )
        ),
        <.tbody(
          Simulation.results.map({ case (tick, num) =>
              <.tr(
                <.td(tick.toString),
                <.td(num.toString)
              )
          })
        )
      )
    )

    /** A function to work out what the local VDOM should look like for the current asteroids */
    def card():VNode = <.div(^.cls := "row",
      <.div(^.cls := "card",
        svg(
          svgRing(Simulation.boundaryRing),
          moleculeNodes
        ),
        <.div(^.cls := "card-footer",
          <.p(s"Tick: ${Simulation.tick}  Outside boundary: ${Simulation.outsideBoundary}"),

          <.div(^.cls := "form-row",
            <.div(^.cls := "btn-group col-md-3",
              <("button")(
                ^.cls := "btn btn-sm btn-secondary", ^.onClick --> stopTicking(),
                <("i")(^.cls := "fa fa-pause")
              ),
              <("button")(
                ^.cls := "btn btn-sm btn-secondary", ^.onClick --> startTicking(),
                <("i")(^.cls := "fa fa-play")
              ),
              <("button")(
                ^.cls := "btn btn-sm btn-secondary", ^.onClick --> reset, "Reset"
              )
            ),

            <.div(^.cls := "input-group col-md-3",
              <.div(^.cls := "input-group-prepend", <.span(^.cls := "input-group-text", "Heat")),
              <("input")(^.attr("type") := "number", ^.cls := "form-control",
                ^.attr("value") := Simulation.heat,
                ^.on("change") ==> { event =>
                  event.target match {
                    case i: HTMLInputElement => Simulation.heat = i.valueAsNumber.toInt
                    case _ => // do nothing
                  }
                }
              )
            )
          )
        )
      ),
      table()
    )

    /** The function we're calling on every tick to re-render this bit of UI */
    def rerender():Unit = {
      // We do our rendering just by telling our component's local root node
      // (the <.div() up in the constructor) to update itself so it has the children that
      // are returned by card(asteroids). ie, we're updating a local virtual DOM.
      val r = try {
        renderElements(card())
        moleculeNodes.foreach(_.update())
      } catch {
        case x:Throwable =>
          renderElements(<.div("Error: " + x.getMessage))
          x.printStackTrace()
      }
    }

    var handle:Option[SetIntervalHandle] = None

    def startTicking(): Unit = {

      def handleTick():Unit = {
        Simulation.step()
        rerender()
      }

      handle = handle orElse Some(timers.setInterval(14)(handleTick()))
    }

    def stopTicking():Unit = {
      handle.foreach(timers.clearInterval)
      handle = None
    }

    def reset():Unit = {
      Simulation.reset()
      resetMoleculeViews()
      rerender()
    }

    override def afterAttach(): Unit = {
      super.afterAttach()
      rerender()
    }

  }


  def main(args:Array[String]): Unit = {
    Simulation.reset()

    val root = Attacher.newRoot(dom.document.getElementById("render-here"))
    root.render(SimulationView)
  }

}