package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{ElementText, PsiNamedElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, isAnonymousExpression}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.{SafeCheckException, extractImplicitParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ImplicitResolveResult, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedMappedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, Set}

/**
  * @author ilyas, Alexander Podkhalyuzin
  */

trait ScExpression extends ScBlockStatement with PsiAnnotationMemberValue with ImplicitParametersOwner
  with ScModificationTrackerOwner with Typeable {

  import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression._

  override def getType(ctx: TypingContext): TypeResult[ScType] =
    this.getTypeAfterImplicitConversion().tr

  @volatile
  protected var implicitParameters: Option[Seq[ScalaResolveResult]] = None

  @volatile
  protected var implicitParametersFromUnder: Option[Seq[ScalaResolveResult]] = None

  /**
    * Warning! There is a hack in scala compiler for ClassManifest and ClassTag.
    * In case of implicit parameter with type ClassManifest[T]
    * this method will return ClassManifest with substitutor of type T.
    *
    * @return implicit parameters used for this expression
    */
  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()

    if (ScUnderScoreSectionUtil.underscores(this).nonEmpty) {
      this.getTypeWithoutImplicits(fromUnderscore = true) //to update implicitParametersFromUnder
      implicitParametersFromUnder
    } else {
      getType(TypingContext.empty) //to update implicitParameters field
      implicitParameters
    }
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] =
    Failure(ScalaBundle.message("no.type.inferred", getText), Some(this))

  /**
    * Some expression may be replaced only with another one
    */
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, removeParenthesis = true)
    }
    val newExpr = if (ScalaPsiUtil.needParentheses(this, expr)) {
      createExpressionFromText(expr.getText.parenthesize(needParenthesis = true))
    } else expr
    val parentNode = oldParent.getNode
    val newNode = newExpr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    newNode.getPsi.asInstanceOf[ScExpression]
  }

  @volatile
  private var additionalExpression: Option[(ScExpression, ScType)] = None

  def setAdditionalExpression(additionalExpression: Option[(ScExpression, ScType)]) {
    this.additionalExpression = additionalExpression
  }

  /**
    * This method should be used to get implicit conversions and used imports, while eta expanded method return was
    * implicitly converted
    *
    * @return mirror for this expression, in case if it exists
    */
  def getAdditionalExpression: Option[(ScExpression, ScType)] = {
    getType(TypingContext.empty)
    additionalExpression
  }

  def implicitElement(fromUnderscore: Boolean = false,
                      expectedOption: => Option[ScType] = this.smartExpectedType()): Option[PsiNamedElement] = {
    def referenceImplicitFunction(reference: ScReferenceExpression) = reference.multiResolve(false) match {
      case Array(result: ScalaResolveResult) => result.implicitFunction
      case _ => None
    }

    def implicitFunction(element: ScalaPsiElement): Option[PsiNamedElement] = element.getParent match {
      case reference: ScReferenceExpression =>
        referenceImplicitFunction(reference)
      case expression@ScInfixExpr(leftOperand, operation, rightOperand)
        if (expression.isLeftAssoc && this == rightOperand) || (!expression.isLeftAssoc && this == leftOperand) =>
        referenceImplicitFunction(operation)
      case call: ScMethodCall => call.getImplicitFunction
      case generator: ScGenerator => implicitFunction(generator)
      case _ =>
        this.getTypeAfterImplicitConversion(expectedOption = expectedOption, fromUnderscore = fromUnderscore).implicitFunction
    }

    implicitFunction(this)
  }

  def getAllImplicitConversions(fromUnderscore: Boolean = false): Seq[PsiNamedElement] = {
    new ScImplicitlyConvertible(this, fromUnderscore)
      .implicitMap(arguments = this.expectedTypes(fromUnderscore).toSeq)
      .map(_.element)
      .sortWith {
        case (first, second) =>
          val firstName = first.name
          val secondName = second.name

          def isAnyTo(string: String): Boolean =
            string.matches("^[a|A]ny(2|To|to).+$")

          val isSecondAnyTo = isAnyTo(secondName)

          if (isAnyTo(firstName) ^ isSecondAnyTo) isSecondAnyTo
          else firstName.compareTo(secondName) < 0
      }
  }

  final def calculateReturns(withBooleanInfix: Boolean = false): Seq[PsiElement] = {
    val res = new ArrayBuffer[PsiElement]

    def calculateReturns0(el: PsiElement) {
      el match {
        case tr: ScTryStmt =>
          calculateReturns0(tr.tryBlock)
          tr.catchBlock match {
            case Some(ScCatchBlock(caseCl)) =>
              caseCl.caseClauses.flatMap(_.expr).foreach(calculateReturns0)
            case _ =>
          }
        case block: ScBlock =>
          block.lastExpr match {
            case Some(expr) => calculateReturns0(expr)
            case _ => res += block
          }
        case pe: ScParenthesisedExpr =>
          pe.expr.foreach(calculateReturns0)
        case m: ScMatchStmt =>
          m.getBranches.foreach(calculateReturns0)
        case i: ScIfStmt =>
          i.elseBranch match {
            case Some(e) =>
              calculateReturns0(e)
              i.thenBranch match {
                case Some(thenBranch) => calculateReturns0(thenBranch)
                case _ =>
              }
            case _ => res += i
          }
        case ScInfixExpr(left, ElementText(op), right)
          if withBooleanInfix && (op == "&&" || op == "||") &&
            left.getType(TypingContext.empty).exists(_ == api.Boolean) &&
            right.getType(TypingContext.empty).exists(_ == api.Boolean) => calculateReturns0(right)
        //TODO "!contains" is a quick fix, function needs unit testing to validate its behavior
        case _ => if (!res.contains(el)) res += el
      }
    }

    calculateReturns0(this)
    res
  }
}

object ScExpression {

  case class ExpressionTypeResult(tr: TypeResult[ScType],
                                  importsUsed: scala.collection.Set[ImportUsed] = Set.empty,
                                  implicitFunction: Option[PsiNamedElement] = None)

  object Type {
    def unapply(exp: ScExpression): Option[ScType] = exp.getType(TypingContext.empty).toOption
  }

  implicit class Ext(val expr: ScExpression) extends AnyVal {
    private implicit def elementScope = expr.elementScope

    private implicit def typeSystem = expr.typeSystem

    def getTypeIgnoreBaseType: TypeResult[ScType] = getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr

    @CachedMappedWithRecursionGuard(expr, Failure("Recursive getNonValueType", Some(expr)), ModCount.getBlockModificationCount)
    def getNonValueType(ctx: TypingContext = TypingContext.empty, //todo: remove?
                        ignoreBaseType: Boolean = false,
                        fromUnderscore: Boolean = false): TypeResult[ScType] = {
      ProgressManager.checkCanceled()
      if (fromUnderscore) expr.innerType(TypingContext.empty)
      else {
        val unders = ScUnderScoreSectionUtil.underscores(expr)
        if (unders.isEmpty) expr.innerType(TypingContext.empty)
        else {
          val params = unders.zipWithIndex.map {
            case (u, index) =>
              val tpe = u.getNonValueType(TypingContext.empty, ignoreBaseType).getOrAny.inferValueType.unpackedType
              Parameter(tpe, isRepeated = false, index = index)
          }
          val methType =
            ScMethodType(expr.getTypeAfterImplicitConversion(ignoreBaseTypes = ignoreBaseType,
              fromUnderscore = true).tr.getOrAny,
              params, isImplicit = false)
          Success(methType, Some(expr))
        }
      }
    }

    /**
      * This method returns real type, after using implicit conversions.
      * Second parameter to return is used imports for this conversion.
      *
      * @param expectedOption  to which type we trying to convert
      * @param ignoreBaseTypes parameter to avoid value discarding, literal narrowing, widening
      *                        this parameter is useful for refactorings (introduce variable)
      */
    @CachedMappedWithRecursionGuard(expr, ExpressionTypeResult(Failure("Recursive getTypeAfterImplicitConversion", Some(expr))), ModCount.getBlockModificationCount)
    def getTypeAfterImplicitConversion(checkImplicits: Boolean = true,
                                       isShape: Boolean = false,
                                       expectedOption: Option[ScType] = None,
                                       ignoreBaseTypes: Boolean = false,
                                       fromUnderscore: Boolean = false): ExpressionTypeResult = {
      val expected = expectedOption.orElse {
        expectedType(fromUnderscore = fromUnderscore)
      }

      if (isShape) {
        val tp: ScType = shape(expr).getOrElse(Nothing)

        expected.filter {
          !tp.conforms(_)
        }.flatMap {
          tryConvertToSAM(fromUnderscore, _, tp)
        }.getOrElse(ExpressionTypeResult(Success(tp, Some(expr))))
      }
      else {
        val tr = getTypeWithoutImplicits(ignoreBaseTypes, fromUnderscore)
        val maybeResult: Option[(ScType, ScalaResolveResult)] = (expected, tr.toOption) match {
          case (Some(expType), Some(tp))
            if checkImplicits && !tp.conforms(expType) => //do not try implicit conversions for shape check or already correct type

            tryConvertToSAM(fromUnderscore, expType, tp) match {
              case Some(r) => return r
              case _ =>
            }

            val scalaVersion = expr.scalaLanguageLevelOrDefault
            if (scalaVersion >= Scala_2_11 && ScalaPsiUtil.isJavaReflectPolymorphicSignature(expr)) {
              return ExpressionTypeResult(Success(expType, Some(expr)))
            }

            val functionType = FunctionType(expType, Seq(tp))
            val implicitCollector = new ImplicitCollector(expr, functionType, functionType, None, isImplicitConversion = true)
            implicitCollector.collect() match {
              case Seq(res) =>
                val `type` = extractImplicitParameterType(res) match {
                  case FunctionType(rt, Seq(_)) => Some(rt)
                  case paramType =>
                    expr.elementScope.cachedFunction1Type.flatMap { functionType =>
                      val (_, substitutor) = paramType.conforms(functionType, ScUndefinedSubstitutor())
                      substitutor.getSubstitutor.map {
                        _.subst(functionType.typeArguments(1))
                      }.filter {
                        !_.isInstanceOf[UndefinedType]
                      }
                    }
                }

                `type`.map((_, res))
              case _ => None
            }
          case _ => None
        }
        maybeResult.map {
          case (tp, result) =>
            ExpressionTypeResult(Success(tp, Some(expr)), result.importsUsed, Some(result.getElement))
        }.getOrElse {
          ExpressionTypeResult(tr)
        }
      }
    }

    @CachedMappedWithRecursionGuard(expr, Failure("Recursive getTypeWithoutImplicits", Some(expr)),
      ModCount.getBlockModificationCount)
    def getTypeWithoutImplicits(ignoreBaseTypes: Boolean = false, fromUnderscore: Boolean = false): TypeResult[ScType] = {
      ProgressManager.checkCanceled()
      expr match {
        case lit: ScLiteral =>
          val typeForNull = lit.getTypeForNullWithoutImplicits
          if (typeForNull.nonEmpty) return Success(typeForNull.get, None)
        case _ =>
      }

      val inner = expr.getNonValueType(TypingContext.empty, ignoreBaseTypes, fromUnderscore)
      inner match {
        case Success(rtp, _) =>
          var res = rtp

          def tryUpdateRes(checkExpectedType: Boolean) {
            if (checkExpectedType) {
              InferUtil.updateAccordingToExpectedType(Success(res, Some(expr)), fromImplicitParameters = true,
                filterTypeParams = false, expectedType = expectedType(fromUnderscore), expr = expr,
                check = checkExpectedType) match {
                case Success(newRes, _) => res = newRes
                case _ =>
              }
            }

            val checkImplicitParameters = ScalaPsiUtil.withEtaExpansion(expr)
            if (checkImplicitParameters) {
              val tuple = InferUtil.updateTypeWithImplicitParameters(res, expr, None, checkExpectedType, fullInfo = false)
              res = tuple._1
              if (fromUnderscore) expr.implicitParametersFromUnder = tuple._2
              else expr.implicitParameters = tuple._2
            }
          }

          @tailrec
          def isMethodInvocation(expr: ScExpression = expr): Boolean = {
            expr match {
              case _: ScPrefixExpr => false
              case _: ScPostfixExpr => false
              case _: MethodInvocation => true
              case p: ScParenthesisedExpr =>
                p.expr match {
                  case Some(exp) => isMethodInvocation(exp)
                  case _ => false
                }
              case _ => false
            }
          }

          if (!isMethodInvocation()) {
            //it is not updated according to expected type, let's do it
            val oldRes = res
            try {
              tryUpdateRes(checkExpectedType = true)
            } catch {
              case _: SafeCheckException =>
                res = oldRes
                tryUpdateRes(checkExpectedType = false)
            }
          }

          def removeMethodType(retType: ScType, updateType: ScType => ScType = t => t) {
            def updateRes(exp: Option[ScType]) {

              exp match {
                case Some(expected) =>
                  expected.removeAbstracts match {
                    case FunctionType(_, _) =>
                    case expect if ScalaPsiUtil.isSAMEnabled(expr) =>
                      val languageLevel = expr.scalaLanguageLevelOrDefault
                      if (languageLevel != Scala_2_11 || ScalaPsiUtil.toSAMType(expect, expr).isEmpty) {
                        res = updateType(retType)
                      }
                    case _ => res = updateType(retType)
                  }
                case _ => res = updateType(retType)
              }
            }

            updateRes(expectedType(fromUnderscore))
          }

          res match {
            case ScTypePolymorphicType(ScMethodType(retType, params, _), tp) if params.isEmpty &&
              !ScUnderScoreSectionUtil.isUnderscore(expr) =>
              removeMethodType(retType, t => ScTypePolymorphicType(t, tp))
            case ScMethodType(retType, params, _) if params.isEmpty &&
              !ScUnderScoreSectionUtil.isUnderscore(expr) =>
              removeMethodType(retType)
            case _ =>
          }

          val valType = res.inferValueType.unpackedType

          if (ignoreBaseTypes) Success(valType, Some(expr))
          else {
            expectedType(fromUnderscore) match {
              case Some(expected) =>
                //value discarding
                if (expected.removeAbstracts equiv Unit) return Success(Unit, Some(expr))
                //numeric literal narrowing
                val needsNarrowing = expr match {
                  case _: ScLiteral => expr.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.tINTEGER
                  case p: ScPrefixExpr => p.operand match {
                    case l: ScLiteral =>
                      l.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.tINTEGER &&
                        Set("+", "-").contains(p.operation.getText)
                    case _ => false
                  }
                  case _ => false
                }

                def checkNarrowing: Option[TypeResult[ScType]] = {
                  try {
                    lazy val i = expr match {
                      case l: ScLiteral => l.getValue match {
                        case i: Integer => i.intValue
                        case _ => scala.Int.MaxValue
                      }
                      case p: ScPrefixExpr =>
                        val mult = if (p.operation.getText == "-") -1 else 1
                        p.operand match {
                          case l: ScLiteral => l.getValue match {
                            case i: Integer => mult * i.intValue
                            case _ => scala.Int.MaxValue
                          }
                        }
                    }
                    expected.removeAbstracts match {
                      case api.Char =>
                        if (i >= scala.Char.MinValue.toInt && i <= scala.Char.MaxValue.toInt) {
                          return Some(Success(Char, Some(expr)))
                        }
                      case api.Byte =>
                        if (i >= scala.Byte.MinValue.toInt && i <= scala.Byte.MaxValue.toInt) {
                          return Some(Success(Byte, Some(expr)))
                        }
                      case api.Short =>
                        if (i >= scala.Short.MinValue.toInt && i <= scala.Short.MaxValue.toInt) {
                          return Some(Success(Short, Some(expr)))
                        }
                      case _ =>
                    }
                  }
                  catch {
                    case _: NumberFormatException => //do nothing
                  }
                  None
                }

                val check = if (needsNarrowing) checkNarrowing else None
                if (check.isDefined) check.get
                else {
                  //numeric widening
                  def checkWidening(l: ScType, r: ScType): Option[TypeResult[ScType]] = {
                    (l, r) match {
                      case (Byte, Short | Int | Long | Float | Double) => Some(Success(expected, Some(expr)))
                      case (Short, Int | Long | Float | Double) => Some(Success(expected, Some(expr)))
                      case (Char, Byte | Short | Int | Long | Float | Double) => Some(Success(expected, Some(expr)))
                      case (Int, Long | Float | Double) => Some(Success(expected, Some(expr)))
                      case (Long, Float | Double) => Some(Success(expected, Some(expr)))
                      case (Float, Double) => Some(Success(expected, Some(expr)))
                      case _ => None
                    }
                  }

                  def getValType: ScType => Option[ScType] = {
                    case AnyVal => Some(AnyVal)
                    case valType: ValType => Some(valType)
                    case designatorType: ScDesignatorType => designatorType.getValType
                    case _ => None
                  }

                  (getValType(valType), getValType(expected)) match {
                    case (Some(l), Some(r)) => checkWidening(l, r) match {
                      case Some(x) => x
                      case _ => Success(valType, Some(expr))
                    }
                    case _ => Success(valType, Some(expr))
                  }
                }
              case _ => Success(valType, Some(expr))
            }
          }
        case _ => inner
      }
    }

    @CachedMappedWithRecursionGuard(expr, Array.empty[ScalaResolveResult], ModCount.getBlockModificationCount)
    def applyShapeResolveForExpectedType(tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation]): Array[ScalaResolveResult] = {
      val applyProc =
        new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */ ,
          StdKinds.methodsOnly, isShapeResolve = true)
      applyProc.processType(tp, expr)
      var cand = applyProc.candidates
      if (cand.length == 0 && call.isDefined) {
        val expr = call.get.getEffectiveInvokedExpr
        ScalaPsiUtil.findImplicitConversion(expr, "apply", expr, applyProc, noImplicitsForArgs = false).foreach { result =>
          val builder = new ImplicitResolveResult.ResolverStateBuilder(result).withImplicitFunction
          applyProc.processType(result.typeWithDependentSubstitutor, expr, builder.state)
          cand = applyProc.candidates
        }
      }
      if (cand.length == 0 && ScalaPsiUtil.approveDynamic(tp, expr.getProject, expr.getResolveScope) && call.isDefined) {
        cand = ScalaPsiUtil.processTypeForUpdateOrApplyCandidates(call.get, tp, isShape = true, isDynamic = true)
      }
      cand
    }

    private def tryConvertToSAM(fromUnderscore: Boolean, expected: ScType, tp: ScType) = {
      def checkForSAM(etaExpansionHappened: Boolean = false): Option[ExpressionTypeResult] = {
        def expectedResult = Some(ExpressionTypeResult(Success(expected, Some(expr))))

        tp match {
          case FunctionType(_, params) if ScalaPsiUtil.isSAMEnabled(expr) =>
            ScalaPsiUtil.toSAMType(expected, expr) match {
              case Some(methodType) if tp.conforms(methodType) => expectedResult
              case Some(methodType@FunctionType(retTp, _)) if etaExpansionHappened && retTp.equiv(Unit) =>
                val newTp = FunctionType(Unit, params)
                if (newTp.conforms(methodType)) expectedResult
                else None
              case _ => None
            }
          case _ => None
        }
      }

      expr match {
        case ScFunctionExpr(_, _) if fromUnderscore => checkForSAM()
        case _ if !fromUnderscore && ScalaPsiUtil.isAnonExpression(expr) => checkForSAM()
        case MethodValue(method) if expr.scalaLanguageLevelOrDefault == Scala_2_11 || method.getParameterList.getParametersCount > 0 =>
          checkForSAM(etaExpansionHappened = true)
        case _ => None
      }
    }

    def expectedType(fromUnderscore: Boolean = true): Option[ScType] =
      expectedTypeEx(fromUnderscore).map(_._1)

    def expectedTypeEx(fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] =
      ExpectedTypes.expectedExprType(expr, fromUnderscore)

    def expectedTypes(fromUnderscore: Boolean = true): Array[ScType] = expectedTypesEx(fromUnderscore).map(_._1)

    @CachedMappedWithRecursionGuard(expr, Array.empty[(ScType, Option[ScTypeElement])], ModCount.getBlockModificationCount)
    def expectedTypesEx(fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])] = {
      ExpectedTypes.expectedExprTypes(expr, fromUnderscore = fromUnderscore)
    }

    @CachedMappedWithRecursionGuard(expr, None, ModCount.getBlockModificationCount)
    def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] = ExpectedTypes.smartExpectedType(expr, fromUnderscore)

  }

  private def shape(expression: ScExpression, ignoreAssign: Boolean = false): Option[ScType] = {
    def shapeIgnoringAssign(maybeExpression: Option[ScExpression]) = maybeExpression.flatMap {
      shape(_, ignoreAssign = true)
    }

    expression match {
      case assign: ScAssignStmt if !ignoreAssign && assign.assignName.isDefined =>
        shapeIgnoringAssign(assign.getRExpression)
      case _ =>
        val arityAndResultType = Option(isAnonymousExpression(expression)).filter {
          case (-1, _) => false
          case _ => true
        }.map {
          case (i, expr: ScFunctionExpr) => (i, shapeIgnoringAssign(expr.result))
          case (i, _) => (i, None)
        }

        arityAndResultType.map {
          case (i, tp) => (Seq.fill(i)(Any), tp)
        }.map {
          case (argumentsTypes, maybeResultType) =>
            FunctionType(maybeResultType.getOrElse(Nothing), argumentsTypes)(expression.elementScope)
        }
    }
  }
}

object ExpectedType {
  def unapply(e: ScExpression): Option[ScType] = e.expectedType()
}

object NonValueType {
  def unapply(e: ScExpression): Option[ScType] = e.getNonValueType().toOption
}
