package cz.zsstudanka.skola.bakakeeper;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.service.SyncProgressListener;
import cz.zsstudanka.skola.bakakeeper.service.SyncResult;

/**
 * CLI implementace SyncProgressListener.
 * Vypisuje průběh synchronizace do standardního výstupu přes ReportManager.
 *
 * @author Jan Hladěna
 */
public class CliProgressListener implements SyncProgressListener {

    private final boolean verbose;

    public CliProgressListener(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void onProgress(String message) {
        if (verbose) {
            ReportManager.log(EBakaLogType.LOG_VERBOSE, message);
        }
    }

    @Override
    public void onResult(SyncResult result) {
        if (!result.isSuccess()) {
            ReportManager.log(EBakaLogType.LOG_ERR, result.toString());
        } else if (verbose && result.getType() != SyncResult.Type.NO_CHANGE) {
            ReportManager.log(EBakaLogType.LOG_OK, result.toString());
        }
    }

    @Override
    public void onPhaseStart(String phaseName) {
        ReportManager.logWait(EBakaLogType.LOG_STDOUT, phaseName);
    }

    @Override
    public void onPhaseEnd(String phaseName, int successCount, int errorCount) {
        if (errorCount > 0) {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            ReportManager.log(EBakaLogType.LOG_STDOUT,
                    phaseName + ": " + successCount + " OK, " + errorCount + " chyb");
        } else {
            ReportManager.logResult(EBakaLogType.LOG_OK);
            if (verbose) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE,
                        phaseName + ": " + successCount + " OK");
            }
        }
    }
}
