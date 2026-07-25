package com.microapproval.api.service;

import com.microapproval.api.entity.EngineType;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.RiskCategory;
import com.microapproval.api.entity.RiskLevel;
import com.microapproval.api.entity.ReviewSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RuleEngineService {

    public List<MicroDecision> analyze(ReviewSession session) {
        String source = session.getRawContent().toLowerCase(Locale.ROOT);
        List<MicroDecision> decisions = new ArrayList<>();

        if (source.contains("select ") && (source.contains("+") || source.contains("${"))) {
            decisions.add(card(session, RiskCategory.SECURITY, RiskLevel.HIGH,
                    "Phát hiện truy vấn SELECT được ghép chuỗi hoặc nội suy biến.",
                    "Truy vấn này có dùng prepared statement hoặc tham số hóa để tránh SQL Injection không?"));
        }
        if (source.contains("drop table") || source.contains("delete from")) {
            decisions.add(card(session, RiskCategory.DATABASE, RiskLevel.HIGH,
                    "Phát hiện lệnh thay đổi hoặc xóa dữ liệu trực tiếp.",
                    "Thao tác dữ liệu này đã được giới hạn phạm vi, kiểm tra điều kiện và có phương án khôi phục chưa?"));
        }
        if (source.contains("password=") || source.contains("api_key") || source.contains("api-key") || source.contains("secret=")) {
            decisions.add(card(session, RiskCategory.SECURITY, RiskLevel.HIGH,
                    "Phát hiện dấu hiệu hardcoded credential hoặc secret.",
                    "Giá trị nhạy cảm này đã được lấy từ secret manager hoặc biến môi trường thay vì ghi trực tiếp trong mã chưa?"));
        }
        if (source.contains("import ") || source.contains("require(") || source.contains("dependencies")) {
            decisions.add(card(session, RiskCategory.DEPENDENCY, RiskLevel.MEDIUM,
                    "Phát hiện thay đổi có thể liên quan đến dependency.",
                    "Dependency mới hoặc được cập nhật này đã được kiểm tra nguồn gốc, license và lỗ hổng bảo mật chưa?"));
        }

        for (int index = 0; index < decisions.size(); index++) {
            decisions.get(index).setDisplayOrder(index + 1);
        }
        return decisions;
    }

    private MicroDecision card(ReviewSession session, RiskCategory category, RiskLevel level, String snippet, String question) {
        return MicroDecision.builder()
                .session(session)
                .engineType(EngineType.RULE_BASED)
                .riskCategory(category)
                .riskLevel(level)
                .codeSnippet(snippet)
                .questionText(question)
                .build();
    }
}
