package com.pistonpunk.gigahack26.lead.web;

import com.pistonpunk.gigahack26.lead.application.BookmarkService;
import com.pistonpunk.gigahack26.policy.application.PolicyLibrary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarks;

    public BookmarkController(BookmarkService bookmarks) {
        this.bookmarks = bookmarks;
    }

    public record Request(String serviceLine, String note) {
    }

    @GetMapping
    public List<BookmarkService.Saved> list() {
        return bookmarks.list();
    }

    @PostMapping("/{companyId}")
    public BookmarkService.Saved add(@PathVariable UUID companyId,
                                     @RequestBody(required = false) Request request) {
        String line = request == null || request.serviceLine() == null || request.serviceLine().isBlank()
                ? PolicyLibrary.AUTOMATION
                : request.serviceLine();
        return bookmarks.add(companyId, line, request == null ? null : request.note());
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity<Void> remove(@PathVariable UUID companyId,
                                       @RequestParam(name = "serviceLine", required = false) String serviceLine) {
        String line = serviceLine == null || serviceLine.isBlank() ? PolicyLibrary.AUTOMATION : serviceLine;
        return bookmarks.remove(companyId, line)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
