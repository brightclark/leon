package leon
package synthesis

import purescala.TreeOps.simplifyLets
import purescala.Trees.Expr
import purescala.Definitions.Program

object SynthesisPhase extends LeonPhase[Program, Program] {
  val name        = "Synthesis"
  val description = "Synthesis"

  override def definedOptions = Set(
    LeonFlagOptionDef("inplace", "--inplace",           "Debug level"),
    LeonFlagOptionDef("derivtrees", "--derivtrees",     "Generate derivation trees")
  )

  def run(ctx: LeonContext)(p: Program): Program = {
    val quietReporter = new QuietReporter
    val solvers  = List(
      new TrivialSolver(quietReporter),
      new FairZ3Solver(quietReporter)
    )

    var inPlace  = false
    var genTrees = false
    for(opt <- ctx.options) opt match {
      case LeonFlagOption("inplace") =>
        inPlace = true
      case LeonFlagOption("derivtrees") =>
        genTrees = true
      case _ =>
    }

    val synth = new Synthesizer(ctx.reporter, solvers, genTrees)
    val solutions = synth.synthesizeAll(p)


    // Simplify expressions
    val simplifiers = List[Expr => Expr](
      simplifyLets _
    )

    val chooseToExprs = solutions.mapValues(sol => simplifiers.foldLeft(sol.toExpr){ (x, sim) => sim(x) })

    if (inPlace) {
      for (file <- ctx.files) {
        new FileInterface(ctx.reporter, file).updateFile(chooseToExprs)
      }
    }

    p
  }

}
