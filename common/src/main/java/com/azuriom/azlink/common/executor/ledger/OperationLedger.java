package com.azuriom.azlink.common.executor.ledger;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

/**
 * Durable local operation ledger. Must survive Minecraft/AzLink restarts.
 */
public interface OperationLedger {

    Optional<LedgerEntry> find(String operationId);

    void put(LedgerEntry entry) throws IOException;

    Collection<LedgerEntry> all();

    void flush() throws IOException;
}
