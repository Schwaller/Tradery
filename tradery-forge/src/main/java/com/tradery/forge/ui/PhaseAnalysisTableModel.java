package com.tradery.forge.ui;

import com.tradery.core.model.PhaseAnalysisResult;
import com.tradery.core.model.PhaseAnalysisResult.Recommendation;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Table model for phase analysis results with sorting support.
 */
public class PhaseAnalysisTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "Phase", "Category",
            "In", "WR In", "Ret In", "PF In",
            "Out", "WR Out", "Ret Out", "PF Out",
            "Rec"
    };

    private List<PhaseAnalysisResult> results = new ArrayList<>();
    private int sortColumn = -1;
    private boolean sortAscending = true;

    public void setResults(List<PhaseAnalysisResult> results) {
        this.results = new ArrayList<>(results);
        if (sortColumn >= 0) {
            sortBy(sortColumn);
        }
        fireTableDataChanged();
    }

    public PhaseAnalysisResult getResultAt(int row) {
        if (row >= 0 && row < results.size()) {
            return results.get(row);
        }
        return null;
    }

    public void sortBy(int column) {
        if (column == sortColumn) {
            sortAscending = !sortAscending;
        } else {
            sortColumn = column;
            sortAscending = true;
        }

        Comparator<PhaseAnalysisResult> comparator = getComparator(column);
        if (!sortAscending) {
            comparator = comparator.reversed();
        }
        results.sort(comparator);
        fireTableDataChanged();
    }

    private Comparator<PhaseAnalysisResult> getComparator(int column) {
        return switch (column) {
            case 0 -> Comparator.comparing(PhaseAnalysisResult::phaseName);
            case 1 -> Comparator.comparing(PhaseAnalysisResult::phaseCategory);
            case 2 -> Comparator.comparingInt(PhaseAnalysisResult::tradesInPhase);
            case 3 -> Comparator.comparingDouble(PhaseAnalysisResult::winRateInPhase);
            case 4 -> Comparator.comparingDouble(PhaseAnalysisResult::totalReturnInPhase);
            case 5 -> Comparator.comparingDouble(PhaseAnalysisResult::profitFactorInPhase);
            case 6 -> Comparator.comparingInt(PhaseAnalysisResult::tradesOutOfPhase);
            case 7 -> Comparator.comparingDouble(PhaseAnalysisResult::winRateOutOfPhase);
            case 8 -> Comparator.comparingDouble(PhaseAnalysisResult::totalReturnOutOfPhase);
            case 9 -> Comparator.comparingDouble(PhaseAnalysisResult::profitFactorOutOfPhase);
            case 10 -> Comparator.comparing(PhaseAnalysisResult::recommendation);
            default -> Comparator.comparing(PhaseAnalysisResult::phaseName);
        };
    }

    @Override
    public int getRowCount() {
        return results.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 2, 6 -> Integer.class;
            case 3, 4, 5, 7, 8, 9 -> Double.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        PhaseAnalysisResult r = results.get(rowIndex);

        return switch (columnIndex) {
            case 0 -> r.phaseName();
            case 1 -> r.phaseCategory();
            case 2 -> r.tradesInPhase();
            case 3 -> r.winRateInPhase();
            case 4 -> r.totalReturnInPhase();
            case 5 -> r.profitFactorInPhase();
            case 6 -> r.tradesOutOfPhase();
            case 7 -> r.winRateOutOfPhase();
            case 8 -> r.totalReturnOutOfPhase();
            case 9 -> r.profitFactorOutOfPhase();
            case 10 -> formatRecommendation(r.recommendation());
            default -> "";
        };
    }

    private String formatRecommendation(Recommendation rec) {
        return switch (rec) {
            case REQUIRE -> "REQUIRE";
            case EXCLUDE -> "EXCLUDE";
            case NEUTRAL -> "-";
        };
    }

    public int getSortColumn() {
        return sortColumn;
    }

    public boolean isSortAscending() {
        return sortAscending;
    }
}
