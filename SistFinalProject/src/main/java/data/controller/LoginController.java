package data.controller;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import data.dto.CompaniesDto;
import data.dto.UserDto;
import data.mapper.LoginMapper;

@Controller
public class LoginController {

	@Autowired
	LoginMapper mapper;
	
	@Autowired
	private KaKaoService kakaoService;
	
	@Autowired
	private PasswordEncoder passwordEncoder;
	
	private String CLIENT_ID = "lAKSwHKCFttsryyK8HWl"; //애플리케이션 클라이언트 아이디값";
	private String CLI_SECRET = "LsHuqKXvaP"; //애플리케이션 클라이언트 시크릿값";
	
	@GetMapping("/login/main")
	public String loginform(HttpSession session, Model model) throws UnsupportedEncodingException, UnknownHostException {
		//아이디
		String myid = (String)session.getAttribute("myid");
		
		//로그인 확인
		String loginok = (String)session.getAttribute("loginok");
		
		//로그인 타입
		String logintype = (String)session.getAttribute("logintype");
		
		//콜백 주소
		String redirectURI = URLEncoder.encode("http://localhost:9003/login/callback1", "UTF-8");
	    SecureRandom random = new SecureRandom();
	    String state = new BigInteger(130, random).toString();
	    String apiURL = "https://nid.naver.com/oauth2.0/authorize?response_type=code"; //api 주소
	    apiURL += String.format("&client_id=%s&redirect_uri=%s&state=%s",
	        CLIENT_ID, redirectURI, state);
	    session.setAttribute("state", state);
	    model.addAttribute("apiURL", apiURL);
		
		if(loginok==null)
			return "/login/loginmain";
		else {
			return "redirect:/mypage/main";
		}
	}
	
	@PostMapping("/login/loginprocess")
	public String loginProcess(
			@RequestParam String id,
			@RequestParam String pass,
			@RequestParam String logintype,
			HttpSession session
			) {
		
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("id", id);
		map.put("pass", pass);
		
		int check = 0;
		String nick = "";
		boolean loginChk = false;
		
		if(logintype.equals("개인회원")) {
			check = mapper.login(map);
			UserDto udto = mapper.getUserLogin(id);
			loginChk = passwordEncoder.matches(pass, udto.getPass()); // 패스워드 매칭(입력한 값, 저장된 값)
			nick = mapper.getName(id);
		} else if(logintype.equals("기업회원")){
			check = mapper.corplogin(map);
			CompaniesDto cdto = mapper.getCorpLogin(id);
			loginChk = passwordEncoder.matches(pass, cdto.getPass());
			nick = mapper.getCorpName(id);
		}
		
		//if(check==1) {
		if(loginChk == true || check == 1) {
			session.setAttribute("myid", id);
			session.setAttribute("loginok", "yes");
			session.setAttribute("nick", nick);
			if(logintype.equals("개인회원")) {
				session.setAttribute("logintype", "user");
			} else if (logintype.equals("기업회원")){
				session.setAttribute("logintype", "corp");
			}
			//체크했을때 on, 안하면 null
			return "redirect:/";
		} else {
			return "/login/passfail";
		}
	}
	
	@GetMapping("/login/logoutprocess")
	public String logout(HttpSession session) {
		
		session.removeAttribute("myid");
		session.removeAttribute("loginok");
		session.removeAttribute("logintype");
		session.removeAttribute("snspass");
		
		return "redirect:main";
	}
	
	@GetMapping("/login/useradd")
	public String useraddform() {
		
		return "/login/useraddform";
	}
	
	//id체크
	@GetMapping("/login/idcheck")
	public @ResponseBody Map<String, Integer> corpIdCheckProc(@RequestParam String id) {
		
		int check = mapper.getIdCheck(id);

		Map<String, Integer> map = new HashMap<String, Integer>();
		map.put("check", check); // 0 or 1
		
		return map;
	}
	
	@PostMapping("/login/insertuser")
	public ModelAndView insertUser(@ModelAttribute UserDto dto, HttpSession session) {
		
		ModelAndView mview = new ModelAndView();
		
		//날짜 형식으로 넣어주기
		dto.setBirth(dto.getBirth1() + "-" + dto.getBirth2() + "-" + dto.getBirth3());
		
		//이메일 형식으로 넣어주기
		dto.setEmail(dto.getEmail1()+ "@" + dto.getEmail2());
		
		//연락처 형식으로 넣어주기
		dto.setHp(dto.getHp1() + "-" + dto.getHp2() + "-" + dto.getHp3());
		
		//패스워드 암호화
		dto.setPass(passwordEncoder.encode(dto.getPass()));
		
		mapper.insertUser(dto);
		
		session.setAttribute("signupType", "user");
		
		mview.addObject("name", dto.getName());
		mview.addObject("id", dto.getId());
		mview.setViewName("/login/addsuccess");
	
		return mview;
	}
	
	@GetMapping("/login/corpadd")
	public String corpAddForm() {
		
		return "/login/corpaddform";
	}
	
	//id체크
	@GetMapping("/login/corpidcheck")
	public @ResponseBody Map<String, Integer> idCheckProc(@RequestParam String id) {
			
		int check = mapper.getCorpIdCheck(id);
		
		Map<String, Integer> map = new HashMap<String, Integer>();
		map.put("check", check); // 0 or 1
		
		return map;
	}
	
	@PostMapping("/login/insertcorp")
	public ModelAndView insertCorp(
			@ModelAttribute CompaniesDto dto,
			@RequestParam MultipartFile logoimage,
			@RequestParam ArrayList<MultipartFile> photoimage,
			HttpSession session) {
		
		ModelAndView mview = new ModelAndView();
		
		String path = session.getServletContext().getRealPath("/images");
		
		String photo = "";
		String logo = logoimage.getOriginalFilename();
		
		try {
			logoimage.transferTo(new File(path + "\\" + logo));
		} catch (IllegalStateException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		if(photoimage.get(0).getOriginalFilename().equals(""))
			photo = "no";
		else {
			for(MultipartFile f:photoimage) {
				String fName = f.getOriginalFilename();
				photo += fName+",";
				
				//업로드
				try {
					f.transferTo(new File(path + "\\" + fName));
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch blockz
					e.printStackTrace();
				}
			}
			photo = photo.substring(0, photo.length()-1); //photo 쉼표 제거
		}
		
		String[] type = {"선택안함", "서비스업", "금융은행업", "IT·정보통신산업", "판매·유통업", "제조·생산·화학업", "미디어·광고업", "기관·협회"};
		for(int i=0; i<type.length; i++) {
			if(dto.getIndustry().equals(Integer.toString(i)))
				dto.setIndustry(type[i]);
		}
		
		//로고 이미지 dto에 저장
		dto.setLogo(logo);
		
		//사진 파일명 저장 (구분기호 , )
		dto.setPhoto(photo);
		
		//날짜 형식으로 저장
		dto.setEstablishment(dto.getBirth1() + ". " + dto.getBirth2() + ". " + dto.getBirth3());
		
		//연락처 형식으로 저장
		dto.setHp(dto.getHp1() + "-" + dto.getHp2() + "-" + dto.getHp3());
		
		//비밀번호 암호화
		dto.setPass(passwordEncoder.encode(dto.getPass()));
		
		mapper.insertCorp(dto);
		
		session.setAttribute("signupType", "corp");
		
		mview.addObject("name", dto.getName());
		mview.addObject("id", dto.getId());
		mview.setViewName("/login/addsuccess");
		
		return mview;
	}
	
	@GetMapping("/login/addsuccess")
	public ModelAndView addSuccessForm(HttpSession session) {
		
		ModelAndView mview = new ModelAndView();
		
		String type = (String)session.getAttribute("loginType");
		
		mview.setViewName("/login/addsuccess");
		
		return mview;
	}
	
	//카카오 로그인 API
	@RequestMapping("/login/kakaoLogin")
    public String home(@RequestParam(value = "code", required = false) String code, 
    		HttpSession session) throws Exception{
		
        System.out.println("#########" + code);
        String access_Token = kakaoService.getAccessToken(code);
        System.out.println("###access_Token### : " + access_Token);
        HashMap<String, Object> userInfo = kakaoService.getUserInfo(access_Token);
        
        String id = (String)userInfo.get("email");
        int idx = id.indexOf("@");
        id = id.substring(0, idx);
        
        String nick = (String)userInfo.get("nickname");
        String email = (String)userInfo.get("email");
        
        if(mapper.getIdCheck(id) == 0) {
        	UserDto dto = new UserDto();
        	dto.setId(id);
            dto.setName(nick);
            dto.setEmail(email);
            mapper.insertUser(dto);
        }
        
        session.setAttribute("nick", nick);
        session.setAttribute("logintype", "user");
        session.setAttribute("snspass", "yes");
        session.setAttribute("loginok", "yes");
        session.setAttribute("myid", id);
        
        return "/login/tomain";
    }
	
}
