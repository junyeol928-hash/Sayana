package com.sayana.dojo.web;

import java.util.List;

import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.progress.HintRecord;

/**
 * 演習カードの表示用。
 *
 * @param code         エディタに最初に入れるコード(最後の提出 or 雛形)
 * @param reviewItemId 復習キューから開いたときの項目ID(通常は null)
 */
public record ExerciseView(ExerciseDef exercise, String code, boolean passed, int attempts,
                           List<HintRecord> hints, Long reviewItemId) {

    public boolean hintsLeft() {
        return hints.size() < 3;
    }
}
