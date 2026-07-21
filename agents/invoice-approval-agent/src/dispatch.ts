import { performance } from "node:perf_hooks";
import { runCreditCheckerSubagent, type CreditCheckerInput } from "./subagents/creditChecker.js";
import { runTaxValidatorSubagent, type TaxValidatorInput } from "./subagents/taxValidator.js";
import type { StructuredFinding, SubagentOutcome, SubagentSource } from "./subagents/types.js";

const CREDIT_CHECKER_TIMEOUT_MS = 5000;
const TAX_VALIDATOR_TIMEOUT_MS = 5000;

class TimeoutError extends Error {}

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new TimeoutError(`timeout after ${ms}ms`)), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}

async function runWithOutcome(
  source: SubagentSource,
  timeoutMs: number,
  task: () => Promise<StructuredFinding>,
): Promise<SubagentOutcome> {
  const startedAt = performance.now();
  try {
    const finding = await withTimeout(task(), timeoutMs);
    return { source, ok: true, startedAt, finishedAt: performance.now(), finding };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return {
      source,
      ok: false,
      startedAt,
      finishedAt: performance.now(),
      error: {
        failure_type: error instanceof TimeoutError ? "timeout" : "error",
        partial_results: null,
        message,
      },
    };
  }
}

/** Nunca rechaza: cualquier fallo (incluido timeout) queda encapsulado en el SubagentOutcome. */
export function runCreditChecker(input: CreditCheckerInput): Promise<SubagentOutcome> {
  return runWithOutcome("credit-checker", CREDIT_CHECKER_TIMEOUT_MS, () => runCreditCheckerSubagent(input));
}

export function runTaxValidator(input: TaxValidatorInput): Promise<SubagentOutcome> {
  return runWithOutcome("tax-validator", TAX_VALIDATOR_TIMEOUT_MS, () => runTaxValidatorSubagent(input));
}
