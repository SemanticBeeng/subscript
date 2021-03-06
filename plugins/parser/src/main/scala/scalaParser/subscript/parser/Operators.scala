package scalaParser.subscript
package parser

import language.implicitConversions
import org.parboiled2._
import scalaParser._
import scalaParser.syntax._

import shapeless._

import scalaParser.subscript.ast.Ast


trait Operators extends Terms {this: SubScript with Exprs =>

  def Expr9: R[Ast.Expr9] = rule(
    Expr9Normal
  | Expr9ShorthandWL
  | Expr9Shorthand
  )

  def Expr9Normal: R[Ast.Expr9] = {
    val col1 = col  // The position of the "="
    rule {IndentedNLSequence({() => Expr8}, col1) ~> Ast.Expr9Seq}
  }


  def Expr9Shorthand: R[Ast.Expr9] =
    Expr9ShorthandGeneric(() => WLR0, () => Term, {c => rule (IdentedNewLine(c) | WSR0)})

  def Expr9ShorthandWL: R[Ast.Expr9] =
    Expr9ShorthandGeneric({() => rule {ch(';')}}, () => Expr8, c => IdentedNewLine(c))

  def Expr9ShorthandGeneric(prefix: () => R0, term: () => R[Ast.Node], separator: Int => R0): R[Ast.Expr9] = {

    def Shorthand[T <: Ast.Node](op: () => R1, generator: Seq[Ast.Node] => T): R[Ast.Expr9] = {
      lazy val col1 = col

      def Trans1: (String, Seq[Ast.Node]) => Ast.Expr9 = (_, terms) => Ast.Expr9Identity(generator(terms))

      rule {prefix() ~ op() ~ WLR0 ~ Code {col1} ~ term().+(separator(col1)) ~> Trans1}
    }

    def OrPar1           = Shorthand(() => `|`   , Ast.OrPar1          )
    def OrPar2           = Shorthand(() => `||`  , Ast.OrPar2          )
    def AndPar1          = Shorthand(() => `&`   , Ast.AndPar1         )
    def AndPar2          = Shorthand(() => `&&`  , Ast.AndPar2         )
    def Equality         = Shorthand(() => `==`  , Ast.Equality        )
    def Alternative      = Shorthand(() => plus  , Ast.Alternative     )
    def Interrupt0OrMore = Shorthand(() => `%/%/`, Ast.Interrupt0OrMore)
    def Interrupt        = Shorthand(() => `%/`  , Ast.Interrupt       )
    def Shuffle1OrMore   = Shorthand(() => `%%`  , Ast.Shuffle1OrMore  )
    def Shuffle          = Shorthand(() => `%`   , Ast.Shuffle         )
    def Disrupt          = Shorthand(() => `/`   , Ast.Disrupt         )

    rule(
      OrPar2
    | OrPar1
    | AndPar2
    | AndPar1
    | Equality
    | Alternative
    | Interrupt0OrMore
    | Interrupt
    | Shuffle1OrMore
    | Shuffle
    | Disrupt
    )
  }


  def Expr8: R[Ast.Expr8] = rule {Expr7.+(wspChR0(';') ~ WLR0) ~> Ast.Expr8}

  def Expr7: R[Ast.Expr7] = {
    def Trans1: (String, Ast.Literal, String, Ast.Expr7, Option[Ast.Expr7]) => Ast.Expr7 = (_, nIf, _, nThen, mElse) =>
      mElse
        .map       {nElse => Ast.IfElseExpr(nIf, nThen, nElse)}
        .getOrElse {Ast.IfExpr(nIf, nThen)}

    def Trans2: (String, Ast.Expr7) => Ast.Expr7 = (_, x) => x

    def doThenElseGen: (Ast.Expr7, Option[Ast.Expr7], Option[Ast.Expr7]) => Ast.Expr7 = (nDo, mThen, mElse) => {
           if ( mThen.isDefined && mElse.isDefined) Ast.DoThenElse(Seq(nDo, mThen.get, mElse.get))
      else if (!mThen.isDefined                   ) Ast.DoElse    (Seq(nDo, mElse.get)           )
      else if (!mElse.isDefined                   ) Ast.DoThen    (Seq(nDo, mThen.get)           )
      else throw new RuntimeException("You must specify at least one of 'then' or 'else' after 'do'")      
    }

    def Trans3: (String, Ast.Expr7, Ast.Expr7, Option[Ast.Expr7]) => Ast.Expr7 = (_, nDo, then, mElse) => doThenElseGen(nDo, Some(then), mElse   )
    def Trans4: (String, Ast.Expr7, Ast.Expr7)                    => Ast.Expr7 = (_, nDo, elseNode   ) => doThenElseGen(nDo, None, Some(elseNode))


    rule (
      `if` ~ WLR0 ~ SimpleValueExpr ~ `then` ~ Expr7 ~ (`else` ~ Expr7 ~> Trans2).? ~> Trans1
    | `do` ~ Expr7 ~ (`then` ~ Expr7 ~> Trans2) ~ (`else` ~ Expr7 ~> Trans2).? ~> Trans3
    | `do` ~ Expr7 ~ (`else` ~ Expr7 ~> Trans2) ~> Trans4
    | Expr6 ~> Ast.IdentityOp
    )
  }

  def Expr6: R[Ast.Expr6] = {
    // TBD: DRY this!
    def Trans1: (Ast.Expr6, Ast.Expr5) => Ast.Expr6 = (accum, next) => accum match {
      case Ast.OrPar1(nodes) => Ast.OrPar1(nodes :+ next)
      case other             => Ast.OrPar1(Seq(other, next))
    }

    def Trans2: (Ast.Expr6, Ast.Expr5) => Ast.Expr6 = (accum, next) => accum match {
      case Ast.OrPar2(nodes) => Ast.OrPar2(nodes :+ next)
      case other             => Ast.OrPar2(Seq(other, next))
    }


    // Wrapped, so that it is Expr6 rather then 5
    def WExpr5: R[Ast.Expr6] = rule {Expr5 ~> {(e: Ast.Expr5) => Ast.OrPar1(Seq(e))}}

    rule {
      WExpr5 ~ zeroOrMore(
        wspStrR0("||") ~ Expr5 ~> Trans2
      | wspChR0 ('|' ) ~ Expr5 ~> Trans1
      )
    }
  }

  def Expr5: R[Ast.Expr5] = {
    def Trans1: (Ast.Expr5, Ast.Expr4) => Ast.Expr5 = (accum, next) => accum match {
      case Ast.AndPar1(nodes) => Ast.AndPar1(nodes :+ next)
      case other             => Ast.AndPar1(Seq(other, next))
    }

    def Trans2: (Ast.Expr5, Ast.Expr4) => Ast.Expr5 = (accum, next) => accum match {
      case Ast.AndPar2(nodes) => Ast.AndPar2(nodes :+ next)
      case other             => Ast.AndPar2(Seq(other, next))
    }


    // Wrapped, so that it is Expr6 rather then 5
    def WExpr4: R[Ast.Expr5] = rule {Expr4 ~> {(e: Ast.Expr4) => Ast.AndPar1(Seq(e))}}

    rule {
      WExpr4 ~ zeroOrMore(
        wspStrR0("&&") ~ Expr4 ~> Trans2
      | wspChR0 ('&' ) ~ Expr4 ~> Trans1
      )
    }
  }

  def Expr4: R[Ast.Expr4] =
    rule {Expr3.+(wspStrR0("==")) ~> Ast.Equality}

  def Expr3: R[Ast.Expr3] =
    rule {Expr2.+(wspChR0('+')) ~> Ast.Alternative}

  def Expr2: R[Ast.Expr2] = {

    // DRY this FIRST PRIORITY!!!
    def Trans1: (Ast.Expr2, Ast.Expr1) => Ast.Expr2 = (accum, next) => accum match {
      case Ast.Interrupt0OrMore(nodes) => Ast.Interrupt0OrMore(nodes :+ next)
      case other                       => Ast.Interrupt0OrMore(Seq(other, next))
    }

    def Trans2: (Ast.Expr2, Ast.Expr1) => Ast.Expr2 = (accum, next) => accum match {
      case Ast.Interrupt(nodes) => Ast.Interrupt(nodes :+ next)
      case other                => Ast.Interrupt(Seq(other, next))
    }

    def Trans3: (Ast.Expr2, Ast.Expr1) => Ast.Expr2 = (accum, next) => accum match {
      case Ast.Shuffle1OrMore(nodes) => Ast.Shuffle1OrMore(nodes :+ next)
      case other                     => Ast.Shuffle1OrMore(Seq(other, next))
    }

    def Trans4: (Ast.Expr2, Ast.Expr1) => Ast.Expr2 = (accum, next) => accum match {
      case Ast.Shuffle(nodes) => Ast.Shuffle(nodes :+ next)
      case other              => Ast.Shuffle(Seq(other, next))
    }

    def Trans5: (Ast.Expr2, Ast.Expr1) => Ast.Expr2 = (accum, next) => accum match {
      case Ast.Disrupt(nodes) => Ast.Disrupt(nodes :+ next)
      case other              => Ast.Disrupt(Seq(other, next))
    }


    // Wrapped, so that it is Expr6 rather then 5
    def WExpr1: R[Ast.Expr2] = rule {Expr1 ~> {(e: Ast.Expr1) => Ast.Disrupt(Seq(e))}}

    rule {
      WExpr1 ~ zeroOrMore(
        wspStrR0("%/%/") ~ Expr1 ~> Trans1
      | wspStrR0("%/"  ) ~ Expr1 ~> Trans2
      | wspStrR0("%%"  ) ~ Expr1 ~> Trans3
      | wspStrR0("%"   ) ~ Expr1 ~> Trans4
      | wspStrR0("/"   ) ~ Expr1 ~> Trans5
      )
    }
  }

  def Expr1: R[Ast.Expr1] = rule {Dataflow.+(WSR0) ~> Ast.Expr1}

  def Dataflow: R[Ast.Dataflow] = {
    def Trans1: (Ast.Term, Option[(Ast.DataflowClause, Seq[Ast.DataflowClause])]) => Ast.Dataflow = (term, maybeClauses) => {
      val initClause = maybeClauses.map(_._1)
      val clauses    = maybeClauses.map(_._2).getOrElse(Nil)

      def maybe(thenClause: Boolean) = initClause.filter(_.thenClause == thenClause).map(Seq(_)).getOrElse(Nil)
      val thenClauses = maybe(true ) ++ clauses.filter( _.thenClause)
      val elseClauses = maybe(false) ++ clauses.filter(!_.thenClause) 
      Ast.Dataflow(term, thenClauses, elseClauses)
    }

    def Trans2: (Ast.DataflowClause, Seq[Ast.DataflowClause]) => (Ast.DataflowClause, Seq[Ast.DataflowClause]) =
      (rhs, lhs) => (rhs, lhs)   // Input is two arguments, output is a tuple. Weird syntax.

    rule {Term ~ (DataflowClause ~ DataflowExtraClause.* ~> Trans2).? ~> Trans1}
  }

  def DataflowClause: R[Ast.DataflowClause] = rule {DataflowThenClause | DataflowElseClause}

  def DataflowClauseGen(head: String, isThenClause: Boolean): R[Ast.DataflowClause] = {
    def Trans1: (String, Ast.Dataflow) => Ast.DataflowClause = (pat, t) => Ast.DataflowClause(pat, t, isThenClause)
    rule {wspStrR0(head) ~ wspChR0('(') ~ CaseClauseHeader ~ wspChR0(')') ~ wspStrR0("~~>") ~ WLR0 ~ Dataflow ~> Trans1}
  }

  def DataflowThenClause: R[Ast.DataflowClause] = DataflowClauseGen("~~" , true )
  def DataflowElseClause: R[Ast.DataflowClause] = DataflowClauseGen("~/~", false)

  def DataflowExtraClause: R[Ast.DataflowClause] = rule {wspStrR0("+") ~ DataflowClause}


  def Term: R[Ast.Term] = rule (DataflowMap | DataflowMapShort | TermRaw)

  def DataflowMap: R[Ast.DataflowMap] = {
    def Trans1: (Ast.Term, Ast.DataflowMapClause, Seq[Ast.DataflowMapClause]) => Ast.DataflowMap =
      (t, c, cs) => Ast.DataflowMap(t, c +: cs)

    rule {TermRaw ~ DataflowMapClause ~ DataflowMapClauseExtra.* ~> Trans1}
  }

  def DataflowMapShort: R[Ast.DataflowMapShort] =
    rule {TermRaw ~ wspStrR0("~~^") ~ StatCtx.Expr ~> Ast.DataflowMapShort}

  def DataflowMapClauseGen(head: String): R[Ast.DataflowMapClause] =
    rule {wspStrR0(head) ~ wspChR0('(') ~ CaseClauseHeader ~ wspChR0(')') ~ wspStrR0("~~^") ~ WLR0 ~ ScalaSimplePrefixExpression ~> Ast.DataflowMapClause}
  
  def DataflowMapClause     : R[Ast.DataflowMapClause] = DataflowMapClauseGen("~~" )
  def DataflowMapClauseExtra: R[Ast.DataflowMapClause] = DataflowMapClauseGen("+~~")

  def TermRaw: R[Ast.Term] = rule {!SSOperator ~ (
    Special
  | WithLaunchAnchor {() => LaunchAnchor }
  | WithLaunch       {() => Launch       }
  | WithParentheses  {() => Parenthesised}
  | Declaration
  | ActorScriptCall
  | FormalParam
  | ScriptCall
  | CodeFragment
  | ScalaTerm
  | Annotation
  )}

  def WrappedExpr9[T](start: String, end: String, generator: Ast.Expr9 => T): R[T] = {
    def Trans1: (String, Option[Ast.Expr9], String) => T = (_, expr, _) => generator(expr.getOrElse(Ast.Expr9Seq(Nil)))
    rule {start ~ WLR0 ~ Expr9.? ~ end ~> Trans1}   
  }

  def LaunchAnchor : R[Ast.LaunchAnchor ] = WrappedExpr9("[**", "**]", Ast.LaunchAnchor )
  def Launch       : R[Ast.Launch       ] = WrappedExpr9("[*" ,  "*]", Ast.Launch       )
  
  def Parenthesised       : R[Ast.Term         ] = rule {ParenthesisedCareted | ParenthesisedRaw}
  def ParenthesisedRaw    : R[Ast.Parenthesised] = WrappedExpr9("["  ,   "]", Ast.Parenthesised)
  def ParenthesisedCareted: R[Ast.Annotation   ] = {
    def Trans1: Ast.Parenthesised => Ast.ScriptCall = {n =>
      val scripted = Ast.nodeToScript(ast.Constants.Name.LAMBDA, n)
      Ast.ScriptCall(Ast.Literal(scripted))
    }

    def ParenthesisedLambda: R[Ast.ScriptCall] =
      rule {ParenthesisedRaw ~> Trans1}

    rule (
      DoubleCaretedNumber {() => ParenthesisedLambda}
    | DoubleCareted       {() => ParenthesisedLambda}
    | Careted             {() => ParenthesisedLambda}
    )
  }

  def Annotation: R[Ast.Annotation] = {
    def Trans1: Ast.ScriptCall => Ast.Literal = _.content.asInstanceOf[Ast.Literal]
    def AnnotationBody: R[Ast.Literal] = rule(CodeFragmentMeta("@{", "}") ~> Ast.Literal | wspChR0('@') ~ ScriptCallOrdinary ~> Trans1)
    rule {WithNormalInScript {() => AnnotationBody} ~ wspChR0(':') ~ WLR0 ~ Term ~> Ast.Annotation}
  }

}
