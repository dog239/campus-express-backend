package com.campusexpress.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusexpress.entity.ExpressPackage;
import com.campusexpress.entity.User;
import com.campusexpress.entity.WarningLog;
import com.campusexpress.mapper.ExpressPackageMapper;
import com.campusexpress.mapper.UserMapper;
import com.campusexpress.mapper.WarningLogMapper;
import com.campusexpress.service.WarningNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTask.class);
    private static final String WARNING_TYPE = "UNCLAIMED_3_DAYS";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExpressPackageMapper expressPackageMapper;
    private final WarningLogMapper warningLogMapper;
    private final UserMapper userMapper;
    private final WarningNotificationService warningNotificationService;

    public ScheduledTask(ExpressPackageMapper expressPackageMapper,
                         WarningLogMapper warningLogMapper,
                         UserMapper userMapper,
                         WarningNotificationService warningNotificationService) {
        this.expressPackageMapper = expressPackageMapper;
        this.warningLogMapper = warningLogMapper;
        this.userMapper = userMapper;
        this.warningNotificationService = warningNotificationService;
    }

    @Scheduled(cron = "${warning.scan-cron:0 0 2 * * ?}")
    public void scanDelayedPackages() {
        LocalDate deadline = LocalDate.now().minusDays(3);
        List<ExpressPackage> packages = expressPackageMapper.selectList(new QueryWrapper<ExpressPackage>()
                .eq("status", 0)
                .eq("deleted", 0)
                .le("arrival_date", deadline));

        for (ExpressPackage expressPackage : packages) {
            try {
                if (alreadyWarned(expressPackage.getId())) {
                    continue;
                }

                User user = userMapper.selectById(expressPackage.getUserId());
                if (user == null) {
                    log.warn("滞留预警跳过：未找到包裹归属用户 packageId={}", expressPackage.getId());
                    continue;
                }

                String message = buildMessage(expressPackage);
                warningNotificationService.pushPackageWarning(user, expressPackage, message);
                insertWarningLog(expressPackage, user, message);
            } catch (Exception ex) {
                log.error("处理滞留预警失败 packageId={}", expressPackage.getId(), ex);
            }
        }
    }

    private boolean alreadyWarned(Long packageId) {
        return warningLogMapper.selectCount(new QueryWrapper<WarningLog>()
                .eq("package_id", packageId)
                .eq("warning_type", WARNING_TYPE)) > 0;
    }

    private void insertWarningLog(ExpressPackage expressPackage, User user, String message) {
        WarningLog warningLog = new WarningLog();
        warningLog.setPackageId(expressPackage.getId());
        warningLog.setUserId(user.getId());
        warningLog.setWarningType(WARNING_TYPE);
        warningLog.setWarningMessage(message);
        warningLog.setPushedAt(LocalDateTime.now());
        warningLog.setCreateTime(LocalDateTime.now());
        warningLogMapper.insert(warningLog);
    }

    private String buildMessage(ExpressPackage expressPackage) {
        return String.format(
                "您的快递（取件码：%s）已在 %s 到站，超过 3 天未领取，请尽快处理。",
                expressPackage.getPickupCode(),
                DATE_FORMATTER.format(expressPackage.getArrivalDate())
        );
    }
}
