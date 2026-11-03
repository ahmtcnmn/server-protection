package com.ahmtcnmn.manager.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.manager.common.RestBaseController;
import com.ahmtcnmn.manager.common.exceptionController.RootEntity;
import com.ahmtcnmn.manager.controller.ImplementController.IRestBaseController;
import com.ahmtcnmn.manager.dto.DtoEventTypeCount;
import com.ahmtcnmn.manager.dto.Event.DtoEventRequest;
import com.ahmtcnmn.manager.dto.Event.DtoEventResponse;
import com.ahmtcnmn.manager.service.EventService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventController extends RestBaseController implements IRestBaseController {

    private final EventService eventService;


    @GetMapping("/{id}/events")
    public Page<DtoEventResponse> getEventsByAgentId(@PathVariable Long id, Pageable pageable) {
        return eventService.listEvents(id, null, null, null, null, null, null, pageable);
    }

    @GetMapping("/{id}/event-type-breakdown")
    public List<DtoEventTypeCount> getEventTypeBreakdown(@PathVariable Long id) {
        return eventService.getEventTypeBreakdown(id);
    }
    

    @Override
    @PostMapping("/create")
    public RootEntity<String> createEvent(@Valid @RequestBody DtoEventRequest eventRequest, HttpServletRequest request) {
        return ok(eventService.createEvent(eventRequest,request));
        
    }

    @GetMapping
    public Page<DtoEventResponse> listEvents(
        @RequestParam(required = false) Long agentId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Integer severityMin,
        @RequestParam(required = false) Integer severityMax,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String search,
        Pageable pageable
    ) {
        return eventService.listEvents(agentId, type, severityMin, severityMax, from, to, search, pageable);
    }

    
    
    
    
    
}
