/* Copyright 2009-2015 EPFL, Lausanne */

package leon
package solvers

import utils.{DebugSectionSolver, Interruptible}
import purescala.Expressions._
import purescala.Common.Identifier
import verification.VC

trait Solver extends Interruptible {
  def name: String
  val context: LeonContext

  implicit lazy val leonContext = context

  def assertCnstr(expression: Expr): Unit
  def assertVC(vc: VC) = {
    assertCnstr(Not(vc.condition))
  }

  def check: Option[Boolean]
  def getModel: Map[Identifier, Expr]
  def getResultSolver: Option[Solver] = Some(this)

  def free()

  def reset()

  def push(): Unit
  def pop(): Unit

  def checkAssumptions(assumptions: Set[Expr]): Option[Boolean]
  def getUnsatCore: Set[Expr]

  implicit val debugSection = DebugSectionSolver

  private[solvers] def debugS(msg: String) = {
    context.reporter.debug("["+name+"] "+msg)
  }
}
