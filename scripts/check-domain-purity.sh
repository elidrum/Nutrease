#!/usr/bin/env bash
# ============================================================
# check-domain-purity.sh
#
# Scansiona app/src/main/java/com/example/nutrease/domain per
# import proibiti (vedi docs/architecture.md, ADR-0007).
#
# La regola: il package `domain/` deve essere Kotlin puro per
# facilitare il porting Flutter/KMP. Nessun import di:
#   - android.*
#   - androidx.*
#   - io.github.jan.supabase.*  (supabase-kt)
#   - androidx.room.*
#   - java.time.*               (usa kotlinx-datetime)
#   - dagger.*                  (eccetto javax.inject.Inject sui UseCase)
#
# Uscita:
#   0  se il dominio è pulito
#   1  se ci sono violazioni
#
# Uso:
#   ./scripts/check-domain-purity.sh
#   ./scripts/check-domain-purity.sh --quiet   (solo exit code)
# ============================================================

set -euo pipefail

QUIET=0
if [[ "${1:-}" == "--quiet" ]]; then
  QUIET=1
fi

DOMAIN_DIR="app/src/main/java/com/example/nutrease/domain"

if [[ ! -d "$DOMAIN_DIR" ]]; then
  # Il package non esiste ancora — OK, non è un errore (siamo pre-Sprint 1)
  [[ $QUIET -eq 0 ]] && echo "ℹ️  $DOMAIN_DIR non esiste ancora. Skip."
  exit 0
fi

# Pattern proibiti (grep -E)
FORBIDDEN='^import (android\.|androidx\.|io\.github\.jan\.supabase\.|androidx\.room\.|java\.time\.|java\.util\.Date|dagger\.)'

# Eccezione esplicita: javax.inject.Inject è ammesso (compromesso DI, ADR-0007)
# Tutto il resto di dagger.* è vietato.

VIOLATIONS=$(grep -rnE "$FORBIDDEN" "$DOMAIN_DIR" --include="*.kt" || true)

if [[ -z "$VIOLATIONS" ]]; then
  [[ $QUIET -eq 0 ]] && echo "🟢 domain/ è puro — nessun import proibito."
  exit 0
fi

echo "🔴 VIOLAZIONI trovate nel package domain/ :"
echo ""
echo "$VIOLATIONS" | while IFS=: read -r file line content; do
  echo "  • $file:$line"
  echo "      $content"
done
echo ""
echo "Fix: sposta la dipendenza nel layer data/ o ui/."
echo "Vedi docs/architecture.md § ADR-0007 per la regola completa."
exit 1
