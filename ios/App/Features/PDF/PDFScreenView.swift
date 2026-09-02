import SwiftUI
import PDFKit
import UIKit

struct PDFScreenView: View {
    private enum Phase {
        case loading
        case loaded(Data)
        case failed
    }

    let restaurant: Restaurant
    @Environment(\.dismiss) private var dismiss
    @State private var phase: Phase = .loading
    @State private var pdfURL: URL?

    init(restaurant: Restaurant) {
        self.restaurant = restaurant
    }

    var body: some View {
        NavigationStack {
            Group {
                switch phase {
                case .loading:
                    VStack(spacing: 12) {
                        ProgressView()
                        Text("pdf.loading").foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                case .failed:
                    VStack(spacing: 16) {
                        Image(systemName: "doc.badge.exclamationmark")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text("pdf.error")
                        Button {
                            Task { await load() }
                        } label: {
                            Label(NSLocalizedString("pdf.error", comment: ""), systemImage: "arrow.clockwise")
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                case .loaded(let data):
                    PDFViewRepresentable(data: data)
                }
            }
            .navigationTitle(Text(String(format: NSLocalizedString("pdf.title", comment: ""), restaurant.displayName)))
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel(Text("done"))
                }
                if let url = pdfURL {
                    ToolbarItem(placement: .topBarTrailing) {
                        ShareLink(item: url) {
                            Image(systemName: "square.and.arrow.up")
                        }
                        .accessibilityLabel(Text("pdf.open_other"))
                    }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        let cache = JSONCache()
        var links: [String: String]? = await cache.read(JSONCache.pdfKey, ttl: CacheTTL.pdfLinks)
        if links == nil {
            do {
                let fresh = try await MenuAPI().pdfLinks()
                links = fresh
                try? await cache.write(JSONCache.pdfKey, fresh)
            } catch {
                links = nil
            }
        }
        guard let links, let url = MenuAPI().pdfLink(for: restaurant, in: links) else {
            phase = .failed
            return
        }
        pdfURL = url
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            phase = .loaded(data)
        } catch {
            phase = .failed
        }
    }
}

private struct PDFViewRepresentable: UIViewRepresentable {
    let data: Data

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.document = PDFDocument(data: data)
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        if uiView.document == nil {
            uiView.document = PDFDocument(data: data)
        }
    }
}
