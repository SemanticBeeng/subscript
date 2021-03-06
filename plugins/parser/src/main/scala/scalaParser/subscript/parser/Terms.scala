package scalaParser.subscript
package parser

import language.implicitConversions
import org.parboiled2._
import scalaParser._
import scalaParser.syntax._

import shapeless._

import scalaParser.subscript.ast.Ast


trait Terms {this: Operators with SubScript with Exprs with Switches =>

  def ScriptTerm: R[Ast.Literal] =
    rule {IdS ~> Ast.Literal}

  def ValueExpr: R[Ast.Literal] = ScriptTerm

  def SimpleValueExpr: R[Ast.Literal] = rule { WithNormalInScript {() => StatCtx.Expr} ~> Ast.Literal}
  
  def ScalaSimplePrefixExpression: R1 = rule {capture(WLR0 ~ anyOf("-+~!")).? ~> ExtractOpt ~ StatCtx.SimpleExpr ~> Concat}


  def ScriptCall: R[Ast.Term] = rule (
    DoubleCaretedNumber {() => VarCallCaretPrefix}
  | DoubleCareted       {() => VarCallCaretPrefix}
  | Careted            ({() => VarCallCaretPrefix}, false)

  | DoubleCaretedNumber {() => ScriptCallRaw}
  | DoubleCareted       {() => ScriptCallRaw}
  | Careted             {() => ScriptCallRaw}
  
  | ScriptCallRaw
  )

  def ScriptCallBase: R1 = {
    def Call = rule {StableIdS ~ ((!WLOneOrMoreR0 ~ TypeArgs).? ~> ExtractOpt) ~ ((!WLOneOrMoreR0 ~ ArgList).? ~> ExtractOpt) ~> Concat3}
    rule {Call.+(ch('.')) ~> ConcatSeqDot}
  }

  def VarCallCaretPrefix: R[Ast.Normal] = {
    def Trans1: Ast.ScriptCall => String = n => {
      val varId: String = Ast.metaString(n.content.asInstanceOf[Ast.Literal].content)
      s"""subscript.DSL._maybeVarCall($varId)"""
    }

    rule {wspChR0('^') ~ !WLOneOrMoreR0 ~ ScriptCallRaw ~> Trans1 ~> Ast.Normal}
  }

  def ScriptCallRaw: R[Ast.ScriptCall] = rule {!(SSOperatorOrKeyword | `^`) ~ (
    ScriptCallNice
  | ScriptCallOrdinary
  )}

  def ScriptCallOrdinary: R[Ast.ScriptCall] =
    rule {ScriptCallBase ~> Ast.Literal ~> Ast.ScriptCall}

  def ScriptCallNice: R[Ast.ScriptCall] = {
    def Trans1: (Option[String], String) => (String, String) = (suffix, arg) => {
      val suffixUpper = suffix.map {s => s.head.toString.toUpperCase + s.tail.mkString}.getOrElse("")
      (suffixUpper, arg)
    }

    def Trans2: Seq[(String, String)] => (String, String) = {seq =>
      val funNameSuffixes: Seq[String] = seq.map(_._1)
      val args           : Seq[String] = seq.map(_._2)

      (funNameSuffixes.mkString, s"(${args.mkString(", ")})")
    }

    def Trans3: (String, (String, String)) => String = {case (base, (suffix, args)) => s"$base$suffix$args"}

    def Trans4: (String, Seq[(String, String)]) => Seq[(String, String)] = (head, tail) => ("", head) +: tail

    def Trans5: Option[Seq[(String, String)]] => Seq[(String, String)] = _.getOrElse(Nil)

    def Trans6: (String, String) => String = (base, arg) => s"$base($arg)"

    // Rules
    def CompactCall: R1 = rule {ScriptCallBase ~ wspChR0(':') ~ ExprsStatHead ~> Trans6}

    def CompactCallOrExpr: R1 = rule(
      CompactCall
    | ScalaSimplePrefixExpression
    )

    def ExprsStat    : R[(String, String)]      = rule {ExprsStatHead ~ ((wspChR0(',') ~ ExprsStatTail).? ~> Trans5) ~> Trans4 ~> Trans2}
    def ExprsStatHead: R1                       = rule {                                  WSR0 ~ WithNiceScriptCall {() => CompactCallOrExpr}}
    def ExprsStatTail: R[Seq[(String, String)]] = rule { ((WSR0 ~ IdS ~ wspChR0(':')).? ~ WSR0 ~ WithNiceScriptCall {() => CompactCallOrExpr} ~> Trans1).+(ch(',')) }

    rule {ScriptCallBase ~ wspChR0(':') ~ ExprsStat ~> Trans3 ~> Ast.Literal ~> Ast.ScriptCall}
  }



  // Code fragments
  def CodeFragment: R[Ast.Term] = rule (
    DoubleCaretedNumber {() => CodeFragmentRaw}
  | DoubleCareted       {() => CodeFragmentRaw}
  | Careted             {() => CodeFragmentRaw}
  | CodeFragmentRaw
  )

  def CodeFragmentRaw: R[Ast.CodeFragment] = {
    def Body = rule (
        EventhandlingLoop
      | Threaded
      | Unsure
      | Normal
      | Eventhandling
      | Tiny
    )

    WithNormalInScript {() => Body}
  }

  def CodeFragmentMeta(symbolStart: String, symbolEnd: String): R1 = {
    rule {wspStrR0(symbolStart) ~ Block ~ wspStrR0(symbolEnd)}
  }

  def CodeFragmentSimpleMeta[T <: Ast.CodeFragment](symbol: String, generator: String => T): R[T] =
    rule {CodeFragmentMeta(s"{$symbol", s"$symbol}") ~> generator}

  def Normal           : R[Ast.Normal           ] = CodeFragmentSimpleMeta("!"   , Ast.Normal          )
  def Threaded         : R[Ast.Threaded         ] = CodeFragmentSimpleMeta("*"  , Ast.Threaded         )
  def Unsure           : R[Ast.Unsure           ] = CodeFragmentSimpleMeta("?"  , Ast.Unsure           )
  def Tiny             : R[Ast.Tiny             ] = CodeFragmentSimpleMeta(":"  , Ast.Tiny              )
  def Eventhandling    : R[Ast.Eventhandling    ] = CodeFragmentSimpleMeta("."  , Ast.Eventhandling    )
  def EventhandlingLoop: R[Ast.EventhandlingLoop] = CodeFragmentSimpleMeta("...", Ast.EventhandlingLoop)

  // Declarations
  def Declaration: R[Ast.Declaration] = rule {VarDecl | ValDecl}

  def StandardDecl[T <: Ast.Declaration](keyword: () => Rule1[String], generator: (String, Option[String], Ast.Node) => T) = {
    def Trans1: (String, String, Option[String], String, Ast.Literal) => T =
      (_, id, tpe, _, expr) => generator(id, tpe, expr)

    rule {keyword() ~ IdS ~ (`:` ~ Spaces(() => Type) ~> SecondStr).? ~ `=` ~ WSR0 ~ SimpleValueExpr ~> Trans1}
  }

  def VarDecl: R[Ast.VarDecl] = StandardDecl(() => `var`, Ast.VarDecl)
  def ValDecl: R[Ast.ValDecl] = StandardDecl(() => `val`, Ast.ValDecl)


  // Special leafs
  def Special: R[Ast.Term] = rule (
    Delta
  | Epsilon
  | Neutral
  | WhileLeaf
  | Let
  | DoFragment
  | Loop
  | OptionalBreakLoop
  | OptionalBreak
  | Break
  )

  def SpecialConstant(rle: () => R1, counterpart: Ast.SpecialConstant): R[Ast.SpecialConstant] =
    rule (rle() ~> {_: String => counterpart})

  def SpecialConstant(symbol: String, counterpart: Ast.SpecialConstant): R[Ast.SpecialConstant] =
    SpecialConstant(() => wspStrR1(symbol), counterpart)

  def Delta             = SpecialConstant("[-]"   , Ast.Delta            )
  def Epsilon           = SpecialConstant("[+]"   , Ast.Epsilon          )
  def Neutral           = SpecialConstant("[]"    , Ast.Neutral          )
  def Loop              = SpecialConstant("..."   , Ast.Loop             )
  def OptionalBreakLoop = SpecialConstant("..?"   , Ast.OptionalBreakLoop)
  def OptionalBreak     = SpecialConstant("break?", Ast.OptionalBreak    )
  def Break             = SpecialConstant({ () => rule {wspStrR1("break") ~ !(CharPredicate.AlphaNum | ch('_') | ch('$'))} } , Ast.Break)


  def WhileLeaf: R[Ast.While] = {
    def Trans1: (String, String, String, String) => Ast.While = (_, _, condition, _) => Ast.While(condition)
    def Trans2: (String, String)                 => Ast.While = (_,    condition   ) => Ast.While(condition)
  
    def Standard: R[Ast.While] = rule( `while` ~ WSR0 ~ '(' ~ StatCtx.Expr ~ ')' ~> Trans1 )
    def Nice    : R[Ast.While] = rule( `while` ~ WSR0 ~ WithNiceScriptCall {() => ScalaSimplePrefixExpression} ~> Trans2)

    rule (Standard | Nice)
  }


  // START: Shorthands for one linear code fragments
  def Let: R[Ast.Tiny] = rule {`let` ~ WSR0 ~ StatCtx.Expr ~ (WSR0 ~ ch(';')).? ~> SecondStr ~> Ast.Tiny}
  
  def DoFragment: R[Ast.CodeFragment] = {
    def Body = rule (
        DoEventhandlingLoop
      | DoThreaded
      | DoUnsure
      | DoNormal
      | DoEventhandling
    )

    WithNormalInScript {() => Body}
  }

  def DoFragmentMeta[T <: Ast.CodeFragment](symbol: String, generator: String => T): R[T] =
    rule {`do` ~ str(symbol) ~ WSR0 ~ StatCtx.Expr ~ (WSR0 ~ ch(';')).? ~> SecondStr ~> generator}

  def DoNormal           : R[Ast.Normal           ] = DoFragmentMeta("!"  , Ast.Normal           )
  def DoThreaded         : R[Ast.Threaded         ] = DoFragmentMeta("*"  , Ast.Threaded         )
  def DoUnsure           : R[Ast.Unsure           ] = DoFragmentMeta("?"  , Ast.Unsure           )
  def DoEventhandling    : R[Ast.Eventhandling    ] = DoFragmentMeta("."  , Ast.Eventhandling    )
  def DoEventhandlingLoop: R[Ast.EventhandlingLoop] = DoFragmentMeta("...", Ast.EventhandlingLoop)
  // END: Shorthands for one linear code fragments


  // Actors
  def ActorScriptCall: R[Ast.ScriptCall] = rule {ActorCall ~> Ast.ScriptCall}

  def ActorCall: R[Ast.ActorCall] = {
    def Trans1: Ast.ActorShortClause => Seq[Ast.ActorShortClause] = x => Seq(x)
    rule {wspStrR0("<<") ~ WLR0 ~ (ActorShortClause ~> Trans1 | ActorCaseClause.+) ~ wspStrR0(">>") ~> Ast.ActorCall}
  }

  def PatternWithGuard: R1 = rule {Pat ~ (ExprCtx.Guard.? ~> ExtractOpt) ~> Concat}

  def ActorCaseClause: R[Ast.ActorCaseClause] =
    rule( WLR0 ~ `case` ~ WithPattern {() => PatternWithGuard} ~> Concat ~ (`=>` ~ WithNormalInScript {() => Block} ~> Concat).? ~ (wspStrR0("==>") ~ ScriptBody).? ~> Ast.ActorCaseClause )
  
  def ActorShortClause: R[Ast.ActorShortClause] =
    rule( WSR0 ~ WithPattern {() => PatternWithGuard} ~ (`=>` ~ WithNormalInScript {() => Block} ~> Concat).? ~ (wspStrR0("==>") ~ ScriptBody).? ~> Ast.ActorShortClause )



  // Scala terms
  def ScalaTerm: R[Ast.Term] = rule (
    DoubleCaretedNumber {() => CaretPrefixedScalaTerm}
  | DoubleCareted       {() => CaretPrefixedScalaTerm}
  | Careted            ({() => CaretPrefixedScalaTerm}, false)
  | ScalaTermRaw ~> Ast.Literal ~> Ast.ScriptCall
  )

  def ScalaTermRaw: R1 = rule (
    BlockExpr
  | Literal
  | ScalaExprTerm  
  | ScalaTupleTerm
  | StatCtx.New
  )
  
  def CaretPrefixedScalaTerm: R[Ast.Normal] =
    rule {wspChR0('^') ~ !WLOneOrMoreR0 ~ ScalaTermRaw ~> Ast.Normal}

  def ScalaExprTerm : R[String] = rule {wspChR0('(') ~ StatCtx.Expr ~ wspChR0(')')}
  def ScalaTupleTerm: R[String] = rule {'(' ~ OneOrMore(() => StatCtx.Expr, () => ',') ~ ')' ~> Concat3}


  // Formal params

  def FormalParam: R[Ast.FormalParam] = rule (
    AdaptingParam
  | ConstrainedParam
  | OutputParam
  )

  def OutputParam: R[Ast.OutputParam] =
    rule {wspChR0('?') ~ !SSKeyword ~ capture(Identifiers.Id) ~> Ast.Literal ~> Ast.OutputParam}

  def ConstrainedParam: R[Ast.ConstrainedParam] = {    
    def Trans1: (Ast.Node, Ast.Node) => Ast.ConstrainedParam = (id, condition) => Ast.ConstrainedParam(id, condition)
    rule {wspChR0('?') ~ !SSKeyword ~ (capture(Identifiers.Id) ~> Ast.Literal) ~ wspStrR0("?if") ~ WSR0 ~ (ScalaSimplePrefixExpression ~> Ast.Literal) ~> Trans1}
  }

  def AdaptingParam: R[Ast.AdaptingParam] =
    rule {wspStrR0("??") ~ !SSKeyword ~ capture(Identifiers.Id) ~> Ast.Literal ~> Ast.AdaptingParam}

}
