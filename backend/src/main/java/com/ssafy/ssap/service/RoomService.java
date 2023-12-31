package com.ssafy.ssap.service;

import com.ssafy.ssap.domain.studyroom.Participant;
import com.ssafy.ssap.domain.studyroom.ParticipantRoleNs;
import com.ssafy.ssap.domain.studyroom.Room;
import com.ssafy.ssap.domain.studyroom.RoomLog;
import com.ssafy.ssap.domain.user.User;
import com.ssafy.ssap.dto.RoomCreateDto;
import com.ssafy.ssap.dto.RoomDto;
import com.ssafy.ssap.repository.*;
import com.ssafy.ssap.util.RoomUtil;
import io.openvidu.java.client.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final Logger logger = LoggerFactory.getLogger(RoomService.class);
    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantRoleNsRepository participantRoleNsRepository;
    private final RoomLogRepository roomLogRepository;
    private final UserRepository userRepository;
    private final RoomUtil roomutil;
    private OpenVidu openVidu;

//    @SuppressWarnings("unused")
//    @Value("${openvidu.local.url}")
//    private String localUrl;
//
//    @SuppressWarnings("unused")
//    @Value("${openvidu.local.secret}")
//    private String localSecret;
//
//    @SuppressWarnings("unused")
//    @Value("${openvidu.server.url}")
//    private String serverUrl;
//
//    @SuppressWarnings("unused")
//    @Value("${openvidu.server.secret}")
//    private String serverSecret;


    @PostConstruct
    public void roomServiceInitialize(){
        String OPENVIDU_URL = "https://i9d205.p.ssafy.io:8443/";
        String SECRET = "MY_SECRET";
        openVidu = new OpenVidu(OPENVIDU_URL,SECRET);
    }

    @Transactional
    public Map<String, Object> makeSession(RoomCreateDto roomCreateDto) throws OpenViduJavaClientException, OpenViduHttpException {
        //session 생성, connection 생성, 토큰 생성
        Map<String, Object> resultMap = new HashMap<>();
        Session session;
        Connection connection;
        try {
            //세션 생성
            session = openVidu.createSession();
            logger.debug("세션 생성 성공");
            //connection 생성
            connection = createConnection(session.getSessionId(), OpenViduRole.MODERATOR);
            resultMap.put("token", connection.getToken());
            logger.debug("커넥션 생성 성공");

            //세션 생성 성공 시 db에 입력
            Room room = Room.builder()
                    .title(roomCreateDto.getTitle())
                    .quota(roomCreateDto.getQuota())
                    .isPrivacy(roomCreateDto.getIsPrivacy())
                    .isValid(true)
                    .sessionId(session.getSessionId())
                    .password(roomCreateDto.getPassword())
                    .endTime(LocalDateTime.now().plusHours(roomCreateDto.getEndHour()).plusMinutes(roomCreateDto.getEndMinute()))
//                    .imagePath(roomCreateDto.getImagePath())
                    .rule(roomCreateDto.getRule())
                    .build();
            roomRepository.save(room);
            resultMap.put("room", room);
            logger.debug("룸 정보 DB 입력");

            addParticipant(room, "호스트", roomCreateDto.getUserNo(), connection.getConnectionId());
            logger.debug("participant 테이블에 레코드 입력");
            addRoomLog(room,roomCreateDto.getUserNo());
            logger.debug("room_log 테이블에 레코드 입력");

            return resultMap;
        } catch (OpenViduJavaClientException | OpenViduHttpException e) {
            logger.error("makeSession 실패");
            throw e;
        }
    }

    public Connection createConnection(String sessionId, OpenViduRole role) throws OpenViduJavaClientException, OpenViduHttpException {
        //열려있는 세션에 참가하고 커넥션(과 커넥션 안의 토큰) 반환

        //입장 요청한 session 객체 획득
        Session session = openVidu.getActiveSession(sessionId);
        if (session == null) {
            logger.error("session Id not matching"+sessionId);
            return null;
        }
        return createConnection(session, role);
    }

    public Connection createConnection(Session session, OpenViduRole role) throws OpenViduJavaClientException, OpenViduHttpException {
        //커넥션 생성 및 반환
        try {
            ConnectionProperties connectionProperties = new ConnectionProperties.Builder()
                    .type(ConnectionType.WEBRTC)
                    .role(role)
                    .build();
            logger.debug("joinRoom token 생성 완료");
            return session.createConnection(connectionProperties);
        } catch (OpenViduJavaClientException | OpenViduHttpException e) {
            logger.error("can't build connection properties or get token from it");
            throw e;
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public String getSessionIdByRoomNo(Integer roomNo) {
        Room room = roomRepository.findById(roomNo).orElse(null);
        if(room==null) logger.error(roomNo+"에 해당하는 방이 DB에 존재하지 않음");
        return room.getSessionId();
    }

    @Transactional
    public void addParticipant(Room room, String role, Integer userNo, String connectionId){
        // 참여자 추가 (방장)
        Participant participant = Participant.builder()
                .isOut(false)
                .role(participantRoleNsRepository.findByName(role))
                .room(room)
                .user(userRepository.findById(userNo).orElse(null))
                .connectionId(connectionId)
                .build();
        participantRepository.save(participant);
        logger.debug("addParticipant 최종 메소드 종료 : "+participant.getId());
    }

    @Transactional
    public void addParticipant(Integer roomNo, String role, Integer userNo, String connectionId) {
        logger.debug("addParticipant 부 함수 진입"+roomNo+"/"+role+"/"+userNo+"/"+connectionId);
        Room room = roomRepository.findById(roomNo).orElse(null);
        try{
            addParticipant(room, role, userNo, connectionId);
            //noinspection DataFlowIssue
            logger.debug("add Participant find Room 결과 :"+room.getId());
        }catch(NullPointerException e){
            logger.error("룸 객체를 찾지 못함 "+e);
        }
    }

    public void addRoomLog(Room room, Integer userId){
        User user = userRepository.findById(userId).orElse(null);
        // 접속 기록 추가
        RoomLog roomLog = RoomLog.builder()
                .roomTitle(room.getTitle())
                .enterTime(LocalDateTime.now())
                .room(room)
                .user(user)
                .build();
        roomLogRepository.save(roomLog);
        logger.debug("addRoomLog 최종 메소드 종료 : "+roomLog.getId());
    }

    @SuppressWarnings("DataFlowIssue")
    public void addRoomLog(Integer roomNo, Integer userNO) {
        try{
            Room room = roomRepository.findById(roomNo).orElse(null);
            addRoomLog(room, userNO);
        } catch(NullPointerException e){
            logger.error("roomNo와 Room객체 매칭 실패");
            throw e;
        }
    }

    /**
     * 스터디룸 폐쇄
     */
    @SuppressWarnings("DataFlowIssue")
    @Transactional
    public void close(Integer roomNo) throws OpenViduJavaClientException, OpenViduHttpException {
        // 방에 접속한 사람들의 room_log 데이터 업데이트
        roomLogRepository.updateSpendHourByAllRoomId(roomNo);
        List<Participant> participantList = participantRepository.findAllByRoomId(roomNo);
        for(Participant p : participantList){
            p.setIsOut(true);
        }

        roomRepository.setValidToZeroByRoomId(roomNo);

        //OpenVIDU 세션 close
        Room room = roomRepository.findById(roomNo).orElse(null);
        Session session;
        try{
            logger.debug("openVidu 디버깅"+openVidu);
            for(Session s : openVidu.getActiveSessions()){
                logger.debug("Iterator:"+s.getSessionId()+"/"+s.getActiveConnections()+"/"+s.getConnections());
            }
            String sessionId = room.getSessionId();
            session = openVidu.getActiveSession(sessionId);

            session.close();
        } catch(NullPointerException e){
            logger.error("can't find room by roomNo");
            throw e;
        } catch (OpenViduJavaClientException | OpenViduHttpException e) {
            logger.error("Openvidu session을 닫는 중 에러 발생");
            throw e;
        }
    }

    public Map<String, Object> getRoom(String code) {
        logger.debug("getRoom, code:"+code);
        Map<String, Object> resultMap = new HashMap<>();
        Room room = roomRepository.findByCode(code).orElse(null);
       try{
           //noinspection DataFlowIssue
           if(!room.getIsValid()){
               resultMap.put("meesage","이 방은 폐쇄되었습니다.");
               return resultMap;
           }
            User host = participantRepository.findUserByRoomIdAndRole(room, participantRoleNsRepository.findByName("호스트"));
            RoomDto roomDto = new RoomDto(room, host.getRoomImagePath());
            Integer num = participantRepository.countByRoomIdAndIsOut(roomDto.getId(),false);
            roomDto.setCurrentNumber(num);

            resultMap.put("room",roomDto);
            resultMap.put("matched",true);
        }catch(NullPointerException e){
           logger.error("code에 해당하는 방 없음"+code+"/"+room.getId());
           logger.error("혹은 host is null");
            resultMap.put("matched",false);
        }
        return resultMap;
    }

    public boolean checkValid(Integer roomNo, String password) throws RuntimeException {
        //방 입장이 가능한지 확인하는 메소드
        logger.debug("[join] checkValid메소드 호출. roomNo:"+roomNo+" password:"+password);
        Room room = roomRepository.findById(roomNo).orElse(null);

        try {
            //noinspection DataFlowIssue
            if (participantRepository.countByRoomIdAndIsOut(roomNo, false) >= room.getQuota()) {
                //check quota
                logger.debug(roomNo + " room is full");
                return false;
            } else {
                logger.debug(roomNo + " room is not full");
            }
            if ((room.getPassword()!=null && password != null) && !room.getPassword().isBlank() && !password.isBlank()) {
                logger.debug("roomPassword, 입력password 둘 다 존재");
                //check password
                if (!room.getPassword().equals(password)) {
                    logger.debug("password 불일치" + room.getPassword() + " / " + password);
                    return false;
                } else {
                    logger.debug("password 일치");
                }
            }
            return true;
        } catch (NullPointerException e){
            logger.error("roomNo와 room 객체 매칭 실패"+roomNo);
            throw e;
        }
    }

    @Transactional
    public void changeHost(Integer roomNo, Integer currentUserNo, Integer nextUserNo) {
        /*
          changeInfo.get("roomNo"), changeInfo.get("currentUserNo"), changeInfo.get("nextUserNo")
          participant테이블의 room_id = roomNo and user_id = nextUserNo 조건에 해당하는 유저의 role을 `방장`으로 바꾼다.
          + 권한부여
          room_id = roomNo and user_id = currentUserNo에 해당하는 유저의 role을 일반으로 바꾼다.
         */
        logger.trace("userNo"+currentUserNo+"/participantNo:"+nextUserNo);

        Participant currentHost = participantRepository.findByRoomIdAndUserId(roomNo, currentUserNo).orElse(null);
        Participant nextHost = participantRepository.findByRoomIdAndUserId(roomNo, nextUserNo).orElse(null);
        final ParticipantRoleNs ROLE_HOST = participantRoleNsRepository.findByName("호스트");

        try{
            //noinspection DataFlowIssue
            if(currentHost.getRole()==ROLE_HOST && nextHost.getRole()!=ROLE_HOST){
                currentHost.setRole(nextHost.getRole());
                nextHost.setRole(ROLE_HOST);
                logger.debug("호스트 role 변경 완료. partiNo "+currentUserNo+" to "+currentHost.getRole().toString()+", partiNo "+nextUserNo+" to "+nextHost.getRole().toString());
            }
        } catch (NullPointerException e) {
            logger.error("user_id와 참여자/호스트 매칭 실패");
            throw e;
        }

    }

    @Transactional
    public void assignStaff(Integer roomNo, Integer userNo) {
        Participant assignee = participantRepository.findByRoomIdAndUserId(roomNo, userNo).orElse(null);
        //주석코드 = 프로버전에서만 동작하는 코드(Update Connection). update connection 없이 role을 기존 참여자에게 부여하려면 connection 재생성 및 기존 connection 삭제, db업데이트 필요
//        Room room = roomRepository.findById(roomNo).orElse(null);

        try{
            //noinspection DataFlowIssue
            if(assignee.getRole() == participantRoleNsRepository.findByName("참여자")){
                //Pro에디션에서만 작동
//                Session session = openVidu.getActiveSession(room.getSessionId());
//                ConnectionProperties connectionProperties = new ConnectionProperties.Builder()
//                        .type(ConnectionType.WEBRTC)
//                        .role(OpenViduRole.MODERATOR)
//                        .build();
//                session.updateConnection(assignee.getConnectionId(), )
                assignee.setRole(participantRoleNsRepository.findByName("스태프"));
            } else{
                logger.error("스태프로 임명 가능한 role(참여자)이 아님");
            }
        }catch (NullPointerException e){
            logger.error("participantNo와 참여자 매칭 실패");
            throw e;
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Transactional
    public HttpStatus exit(Integer roomNo, Integer userNo) {
        String connectionId;
        Session session;
        HttpStatus status;

        RoomLog roomLog = roomLogRepository.findByRoomIdAndUserId(roomNo, userNo).orElse(null);
        Participant participant = participantRepository.findByRoomIdAndUserId(roomNo, userNo).orElse(null);


        try{
            //강제 disconnect 성공 시 db처리
            //participant 테이블 수정 (is_Out 수정)
            participant.setIsOut(true);
            logger.trace("is_out 수정 완료");

            //room_log 수정 (spend hour 갱신)
            LocalDateTime time_start, time_end;
            time_start = roomLog.getEnterTime();
            time_end = LocalDateTime.now();
            int spend_min = Math.toIntExact(ChronoUnit.MINUTES.between(time_start, time_end));
            LocalTime spendTime = LocalTime.of(spend_min / 60, spend_min % 60);

            roomLog.setSpendHour(spendTime);
            logger.trace("spend hour 갱신 완료");
            status = HttpStatus.OK;

            //대상자 connection 강제 disconnect
            connectionId = participant.getConnectionId();
            session = openVidu.getActiveSession(roomLog.getRoom().getSessionId());
            session.forceDisconnect(connectionId);
        } catch(NullPointerException e){
            logger.error("매칭되는 객체 없음"+roomNo+"/"+userNo);
            status = HttpStatus.CONFLICT;
        } catch(OpenViduJavaClientException | OpenViduHttpException e){
            logger.error("Openvidu connection 처리 실패");
            status = HttpStatus.MULTI_STATUS;
        } catch(Exception e){
            logger.error("Another Error Not Expected while exiting Room");
            status = HttpStatus.MULTI_STATUS;
        }
        logger.debug("room/exit 트랜잭션 정상 완료");
        return status;
    }

    public void getEntireRoomList(Map<String,Object> resultMap) {
        List<RoomDto> roomList = roomRepository.findAllValidRooms();
        for(RoomDto roomDto : roomList){
            Integer num = participantRepository.countByRoomIdAndIsOut(roomDto.getId(),false);
            roomDto.setCurrentNumber(num);
        }
        resultMap.put("roomList",roomList);
    }

    public Map<String, Object> getRoomList(String keyword, Integer pageNo, int pageSize) {
        Map<String,Object> resultMap = new HashMap<>();

        Specification<Room> specification = Specification.<Room>where(null)
                .and(RoomSpecifications.hasTitle(keyword))
                .and(RoomSpecifications.isValid(true));
        Pageable pageable = PageRequest.of(pageNo,pageSize, Sort.by(Sort.Direction.DESC,"id"));
        Page<Room> roomList = roomRepository.findAll(specification, pageable);
        try{
            Page<RoomDto> roomDtoList = roomList.map(room -> {
                User host = participantRepository.findUserByRoomIdAndRole(room, participantRoleNsRepository.findByName("호스트"));
                logger.debug("user의 roomiMagePath 조회중"+room.getId()+"/"+host.getRoomImagePath()+"/"+host.getId());
                RoomDto roomDto = new RoomDto(room,host.getRoomImagePath());
                Integer num = participantRepository.countByRoomIdAndIsOut(roomDto.getId(),false);
                roomDto.setCurrentNumber(num);
                return roomDto;
            });

            resultMap.put("roomList",roomDtoList);
        }catch(NullPointerException e){
            logger.error("특정 room에 해당하는 host 찾지 못함"+e);
        }catch(Exception e){
            logger.error("뭔에러고이건"+e);
        }

        return resultMap;
    }

    @Transactional
    public void kickAndAlarm(Integer roomNo, Integer staffNo, Integer targetNo, String reason) {
        /*
        participant테이블의 participantNo가 일치하는 유저의 is_out을 0으로 바꾼다.
        alarm 테이블에 insert (insert into alarm(title, detail, link, user_id) values("강제퇴장 처리", kickinfo.reason, ?, kickInfo.partiNo))
        room_log 테이블에 update (update room_log set exit_time=now() where user_id= ~ and room_id = ~)
        */
        if(roomutil.isStaff(roomNo, staffNo)){
            if(exit(roomNo, targetNo) == HttpStatus.OK){
                //alarmService.createAlarm(room.getTitle()+"방에서 내보내졌습니다.", reason, participant.getUserId());
            }
        }


    }

    @Transactional
    public String joinRoom(Integer roomNo, String password, Integer userNo) throws OpenViduJavaClientException, OpenViduHttpException {
        String sessionId;
        Connection connection=null;
        if(checkValid(roomNo, password)){
            //OPENVIDU > session 접속을 위한 token 생성
            try {
                sessionId = getSessionIdByRoomNo(roomNo);
                connection = createConnection(sessionId, OpenViduRole.PUBLISHER);
                logger.debug("토큰처리 수행 완료 : " + connection + " / " + connection.getConnectionId());
            }catch (OpenViduException e){
                logger.error("joinRoom 오픈비두 관련 에러"+e);
            }
            //DB처리
            try {
                //noinspection DataFlowIssue
                addParticipant(roomNo, "참여자", userNo, connection.getConnectionId()); //4번
                logger.debug("JoinRoom - addParticipant 종료");
                addRoomLog(roomNo, userNo); //5번
                logger.debug("JoinRoom - addRoomLog 종료");
            }catch(Exception e){
                logger.error("DB처리 중 에러 발생"+e);
            }
        } else{
            logger.debug("방 접근이 유효하지 않음.");
            return null;
        }
        return connection.getToken();
    }

    public Map<String, Object> checkConnection(Integer roomNo) {
        Map<String, Object> resultMap = new HashMap<>();
        String sessionId = getSessionIdByRoomNo(roomNo);
        Session session = openVidu.getActiveSession(sessionId);

        List<String> currentOpenvidu = new ArrayList<>();
        for(Connection con : session.getActiveConnections()){
            currentOpenvidu.add(con.getConnectionId());
        }
        resultMap.put("openviduConnections",currentOpenvidu);

        try{
            List<Participant> currentObjectsInDB= participantRepository.findAllByRoomId(roomNo);
            List<String> currentDB = new ArrayList<>();
            for(Participant p : currentObjectsInDB){
                currentDB.add(p.getConnectionId());
            }
            resultMap.put("dbConnections",currentDB);
        } catch(NullPointerException e){
            logger.error("connection 체크 중 오류");
            throw e;
        }
        return resultMap;
    }

    @Transactional
    public Map<String, String> createCode(Integer roomNo) {
        Map<String, String> resultMap = new HashMap<>();

        //1. code생성 2. 기존코드와 중복되는지 검사 3-1. 중복X -> 저장 후 리턴 3-2. 중복 O -> 1부터 반복
        Random random = new Random();
        String code;
        Room room = roomRepository.findById(roomNo).orElse(null);
        if(room == null) {
            resultMap.put("meesage","코드를 생성할 대상 방 찾지 못함");
            logger.error("room 객체 없음");
        } else{
            logger.trace("대상 방"+room);
        }

        try {
            for (int i = 0; i < 1000; i++) {
                code = String.format("%03d", random.nextInt(999) + 1);
                logger.trace("roomCode의 반복문 실행. code:"+code);
                if (roomRepository.findByCode(code).orElse(null) == null) {
                    //code에 해당하는 room이 없으면 해당 코드를 room에 set하고 return
                    //noinspection DataFlowIssue
                    room.setCode(code); //저장용
                    resultMap.put("code",code); //전달용
                    return resultMap;
                }
            }
        } catch(Exception e){
            logger.error("code 생성 중 알 수 없는 에러 : " + e);
        }

        resultMap.put("stopMessage","roomCode생성 시도 1000회 반복으로 중단");
        return resultMap;
    }
}

