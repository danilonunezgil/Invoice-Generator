import "dotenv/config";
import { runInvoiceApprovalLoop } from "./loop.js";

const invoiceId = process.argv[2];
if (!invoiceId) {
  console.error("Uso: node dist/index.js <invoiceId>");
  process.exit(1);
}

runInvoiceApprovalLoop(invoiceId)
  .then((decision) => {
    console.log("\n=== Decisión final ===");
    console.log(decision);
    process.exit(0);
  })
  .catch((error) => {
    console.error("\n=== Error ===");
    console.error(error instanceof Error ? error.message : error);
    process.exit(1);
  });
